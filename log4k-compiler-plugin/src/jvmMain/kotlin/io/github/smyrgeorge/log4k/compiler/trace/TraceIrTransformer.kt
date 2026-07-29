package io.github.smyrgeorge.log4k.compiler.trace

import io.github.smyrgeorge.log4k.compiler.ir.utils.LOG4K_PACKAGE
import io.github.smyrgeorge.log4k.compiler.ir.utils.OfThisClassField
import io.github.smyrgeorge.log4k.compiler.ir.utils.TracingSymbols
import io.github.smyrgeorge.log4k.compiler.ir.utils.buildInlineLambdaExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.dispatchReceiverParam
import io.github.smyrgeorge.log4k.compiler.ir.utils.findLog4kFunction
import io.github.smyrgeorge.log4k.compiler.ir.utils.instrumentationName
import io.github.smyrgeorge.log4k.compiler.ir.utils.isInstrumentationTarget
import io.github.smyrgeorge.log4k.compiler.ir.utils.receiverOrContextOf
import io.github.smyrgeorge.log4k.compiler.ir.utils.regularParams
import io.github.smyrgeorge.log4k.compiler.ir.utils.reportError
import io.github.smyrgeorge.log4k.compiler.ir.utils.resolveTags
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites the body of every function annotated with `io.github.smyrgeorge.log4k.annotation.Traced`
 * so that it is executed inside a new tracing span.
 *
 * The span's **parent** (and the tracer that creates it) is resolved from what is in scope, in order:
 * 1. a `TracingContext` parameter/receiver — the new span nests under its current span (as before);
 * 2. otherwise a `TracingEvent.Span` parameter/receiver — used directly as the parent;
 * 3. otherwise a `trace: Tracer` member (else the class's single `Tracer`-typed property, whatever
 *    its name) — reused, or synthesized as `private val _trace_ = Tracer.of(this::class)` — which
 *    creates a new root span.
 *
 * Given:
 * ```kotlin
 * @Traced(name = "load")
 * context(_: TracingContext)
 * suspend fun load(id: Long): User { /* body */ }
 * ```
 *
 * the body is replaced with (conceptually):
 * ```kotlin
 * suspend fun load(id: Long): User =
 *     TracingContext.traced(context = ctx, parent = null, tracer = null, "load") { /* body */ }
 * ```
 *
 * `TracingContext.traced` is `inline`, so both regular and `suspend` functions work: the moved body
 * is placed in an inline lambda and therefore keeps its original suspension context.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class TraceIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // The `inline fun <T> traced(context, parent, tracer, name, tags, f): T` helper in `TracingContext.Companion`.
    private val tracedFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("TracingContext.Companion", "traced", regularParams = 6)

    // `TracingContext` (nest under its current span) and `TracingEvent.Span` (used as the parent
    // directly) — resolved from a context parameter/receiver of the instrumented function.
    private val tracing = TracingSymbols.of(finder)

    // Reuses a `trace: Tracer` member (else the class's single `Tracer`-typed property), or
    // synthesizes `private val _trace_ = Tracer.of(this::class)`.
    private val tracerField: OfThisClassField? =
        OfThisClassField.of(pluginContext, finder, messageCollector, "Tracer", "@Traced", "trace", "_trace_")

    // Used to materialize @Traced(tags = [...]) as `this.tags.put(key, value)` inside the span lambda.
    private val spanTagsGetter: IrSimpleFunctionSymbol? = finder.findProperties(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("TracingEvent.Span"), false), Name.identifier("tags")),
    ).firstOrNull()?.owner?.getter?.symbol

    private val mutableMapPut: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(FqName("kotlin.collections"), FqName("MutableMap"), false), Name.identifier("put")),
    ).firstOrNull()

    // The log4k tracing API must be on the classpath for the plugin to do anything.
    val isReady: Boolean =
        tracedFunction != null && tracing.tracingContext != null && tracing.span != null && tracerField != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        // @NoTrace on the function — or on its class (a per-class kill switch) — overrides any @Traced.
        if (declaration.isInstrumentationTarget(TRACED_ANNOTATION, NO_TRACE_ANNOTATION)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    /** Attaches every synthesized `_trace_` field to its class. Must run after the module transform. */
    fun commit() = tracerField?.commit()

    private fun instrument(function: IrFunction) {
        val tracedFn = tracedFunction ?: return

        // Resolve the pieces of the `TracingContext.traced` signature we depend on. A mismatch here
        // means the plugin is out of sync with the log4k API, so fail with a clear diagnostic
        // instead of crashing.
        val dispatchParam = tracedFn.owner.dispatchReceiverParam()
        val regular = tracedFn.owner.regularParams()
        val dispatchClass = dispatchParam?.type?.classOrNull
        val fParam = regular.getOrNull(5)
        val spanLocalType = ((fParam?.type as? IrSimpleType)?.arguments?.firstOrNull() as? IrTypeProjection)?.type
        if (dispatchParam == null || dispatchClass == null || regular.size != 6 || spanLocalType == null) {
            messageCollector.reportError(
                function,
                "log4k-compiler-plugin: could not resolve the expected `TracingContext.traced` signature — " +
                        "the plugin is incompatible with this version of log4k.",
            )
            return
        }
        val contextArgParam = regular[0]
        val parentArgParam = regular[1]
        val tracerArgParam = regular[2]
        val nameArgParam = regular[3]
        // regular[4] is `tags` — left to its `emptyMap()` default; @Traced tags are materialized into
        // `span.tags` inside the lambda below.

        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        // Resolve the parent source: a TracingContext, else a Span, else fall back to a Tracer.
        val contextParam = tracing.tracingContext?.let { function.receiverOrContextOf(it) }
        val spanParam = if (contextParam == null) tracing.span?.let { function.receiverOrContextOf(it) } else null
        val contextArg = if (contextParam != null) builder.irGet(contextParam) else builder.irNull(contextArgParam.type)
        val parentArg = if (spanParam != null) builder.irGet(spanParam) else builder.irNull(parentArgParam.type)
        val tracerArg = if (contextParam == null && spanParam == null) {
            // Neither in scope -> create a root span via the class' `trace: Tracer` (errors reported inside).
            tracerField?.access(function) ?: return
        } else {
            builder.irNull(tracerArgParam.type)
        }

        val spanName = function.instrumentationName(TRACED_ANNOTATION)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` with receiver `Span.Local`.
        val lambdaExpression = pluginContext.buildInlineLambdaExpression(
            enclosing = function,
            returnType = returnType,
            extensionReceiverType = spanLocalType,
            extensionReceiverName = Name.identifier($$"$this$span"),
        )
        val lambda = lambdaExpression.function

        // 1b. Materialize `@Traced(tags = [...])` as `this.tags.put(k, v)` at the start of the lambda.
        val tags = function.resolveTags(TRACED_ANNOTATION)
        if (tags.isNotEmpty()) {
            val tagsGetter = spanTagsGetter
            val putFn = mutableMapPut
            val lambdaReceiver = lambda.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
            val lambdaBody = lambda.body as? IrBlockBody
            if (tagsGetter == null || putFn == null || lambdaReceiver == null || lambdaBody == null) {
                messageCollector.reportError(
                    function,
                    "log4k-compiler-plugin: could not apply @Traced tags (unresolved `Span.tags` / `MutableMap.put`).",
                )
                return
            }
            val tagBuilder = DeclarationIrBuilder(pluginContext, lambda.symbol)
            val tagStatements = tags.map { (key, value) ->
                putTag(tagBuilder, lambdaReceiver, tagsGetter, putFn, key, value)
            }
            lambdaBody.statements.addAll(0, tagStatements)
        }

        // 2. `TracingContext.traced<returnType>(context, parent, tracer, "name", <lambda>)`.
        val call = builder.irCall(tracedFn, returnType, listOf(returnType)).apply {
            arguments[dispatchParam] = builder.irGetObjectValue(dispatchParam.type, dispatchClass)
            arguments[contextArgParam] = contextArg
            arguments[parentArgParam] = parentArg
            arguments[tracerArgParam] = tracerArg
            arguments[nameArgParam] = builder.irString(spanName)
            arguments[fParam] = lambdaExpression
        }

        // 3. Replace the original body with `return TracingContext.traced(...) { ... }`.
        function.body = builder.irBlockBody { +irReturn(call) }
    }

    /** Builds `<receiver>.tags.put(key, value)`. */
    private fun putTag(
        builder: DeclarationIrBuilder,
        receiver: IrValueParameter,
        tagsGetter: IrSimpleFunctionSymbol,
        put: IrSimpleFunctionSymbol,
        key: String,
        value: String,
    ): IrExpression {
        val getTags = builder.irCall(tagsGetter).apply {
            tagsGetter.owner.dispatchReceiverParam()?.let { arguments[it] = builder.irGet(receiver) }
        }
        return builder.irCall(put).apply {
            put.owner.dispatchReceiverParam()?.let { arguments[it] = getTags }
            val regular = put.owner.regularParams()
            regular.getOrNull(0)?.let { arguments[it] = builder.irString(key) }
            regular.getOrNull(1)?.let { arguments[it] = builder.irString(value) }
        }
    }

    companion object {
        private val TRACED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Traced")
        private val NO_TRACE_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.NoTrace")
    }
}
