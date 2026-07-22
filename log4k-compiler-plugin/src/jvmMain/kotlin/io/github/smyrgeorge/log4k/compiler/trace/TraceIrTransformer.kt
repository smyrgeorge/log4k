package io.github.smyrgeorge.log4k.compiler.trace

import io.github.smyrgeorge.log4k.compiler.ir.Log4kIrFunctionExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.LOG4K_PACKAGE
import io.github.smyrgeorge.log4k.compiler.ir.utils.isClassLevelEligible
import io.github.smyrgeorge.log4k.compiler.ir.utils.moveBody
import io.github.smyrgeorge.log4k.compiler.ir.utils.qualifiedName
import io.github.smyrgeorge.log4k.compiler.ir.utils.reportError
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites the body of every function annotated with
 * `io.github.smyrgeorge.log4k.annotation.Trace` so that it is executed inside a new
 * tracing span.
 *
 * Given:
 * ```kotlin
 * context(ctx: TracingContext)
 * @Trace(name = "load")
 * suspend fun load(id: Long): User { /* body */ }
 * ```
 *
 * the body is replaced with (conceptually):
 * ```kotlin
 * context(ctx: TracingContext)
 * suspend fun load(id: Long): User = ctx.span("load") { /* body */ }
 * ```
 *
 * `TracingContext.span` is `inline`, so both regular and `suspend` functions work: the
 * moved body is placed in an inline lambda and therefore keeps its original suspension
 * context.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class TraceIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // The `inline fun <T> TracingContext.span(name: String, f: Span.Local.() -> T): T`
    // helper, declared inside `TracingContext.Companion`.
    private val spanFunction: IrSimpleFunctionSymbol? = run {
        val companion = ClassId(LOG4K_PACKAGE, FqName("TracingContext.Companion"), false)
        finder.findFunctions(CallableId(companion, Name.identifier("span"))).firstOrNull { symbol ->
            val regular = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            regular.size == 2 && regular.getOrNull(0)?.type?.isString() == true
        }
    }

    // `TracingContext` — the extension receiver of `span` — used to detect the context parameter.
    private val tracingContextSymbol = spanFunction?.owner?.parameters?.firstOrNull {
        it.kind == IrParameterKind.ExtensionReceiver
    }?.type?.classOrNull

    // Used to materialize @Trace(tags = [...]) as `this.tags.put(key, value)` inside the span lambda.
    private val spanTagsGetter: IrSimpleFunctionSymbol? = finder.findProperties(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("TracingEvent.Span"), false), Name.identifier("tags")),
    ).firstOrNull()?.owner?.getter?.symbol

    private val mutableMapPut: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(FqName("kotlin.collections"), FqName("MutableMap"), false), Name.identifier("put")),
    ).firstOrNull()

    // The log4k tracing API must be on the classpath for the plugin to do anything.
    val isReady: Boolean = tracingContextSymbol != null && spanFunction != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (shouldInstrument(declaration)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    private fun shouldInstrument(function: IrFunction): Boolean {
        if (function.body == null) return false

        val enclosingClass = function.parentClassOrNull
        // @NoTrace on the function — or on its class (a per-class kill switch) — disables tracing,
        // overriding any @Trace.
        if (function.hasAnnotation(NO_TRACE_ANNOTATION)) return false
        if (enclosingClass?.hasAnnotation(NO_TRACE_ANNOTATION) == true) return false

        // Explicit @Trace on the function: instrument (a missing context parameter is a hard error).
        if (function.hasAnnotation(TRACE_ANNOTATION)) return true

        // Class-level @Trace: instrument eligible public member functions that can actually be traced
        // (functions without a TracingContext context parameter are skipped silently).
        if (enclosingClass == null || !enclosingClass.hasAnnotation(TRACE_ANNOTATION)) return false
        return function.isClassLevelEligible() && findContextParam(function) != null
    }

    private fun findContextParam(function: IrFunction): IrValueParameter? {
        val contextSymbol = tracingContextSymbol ?: return null
        return function.parameters.firstOrNull {
            it.kind == IrParameterKind.Context && it.type.isSubtypeOfClass(contextSymbol)
        }
    }

    private fun instrument(function: IrFunction) {
        val spanFn = spanFunction ?: return

        val contextParam = findContextParam(function)
        if (contextParam == null) {
            messageCollector.reportError(
                function,
                "@Trace function '${function.name.asString()}' must declare a TracingContext " +
                        "context parameter, e.g. `context(_: TracingContext)`.",
            )
            return
        }

        // Resolve the pieces of the `TracingContext.span` signature we depend on. A mismatch here
        // means the plugin is out of sync with the log4k API, so fail with a clear diagnostic
        // instead of crashing.
        val spanParams = spanFn.owner.parameters
        val dispatchParam = spanParams.singleOrNull { it.kind == IrParameterKind.DispatchReceiver }
        val extensionParam = spanParams.singleOrNull { it.kind == IrParameterKind.ExtensionReceiver }
        val regularParams = spanParams.filter { it.kind == IrParameterKind.Regular }
        val nameParam = regularParams.getOrNull(0)
        val fParam = regularParams.getOrNull(1)
        val dispatchClass = dispatchParam?.type?.classOrNull
        val spanLocalType = ((fParam?.type as? IrSimpleType)?.arguments?.firstOrNull() as? IrTypeProjection)?.type

        if (dispatchParam == null || extensionParam == null || nameParam == null || fParam == null ||
            dispatchClass == null || spanLocalType == null
        ) {
            messageCollector.reportError(
                function,
                "log4k-compiler-plugin: could not resolve the expected `TracingContext.span` signature — " +
                        "the plugin is incompatible with this version of log4k.",
            )
            return
        }

        val spanName = resolveSpanName(function)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` with receiver `Span.Local`.
        val lambda = pluginContext.irFactory.buildFun {
            name = Name.special("<anonymous>")
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            visibility = DescriptorVisibilities.LOCAL
            modality = Modality.FINAL
            this.returnType = returnType
            isSuspend = false
        }.apply {
            parent = function
            val receiver = buildValueParameter(this) {
                name = Name.identifier($$"$this$span")
                kind = IrParameterKind.ExtensionReceiver
                type = spanLocalType
            }
            parameters = listOf(receiver)
            body = pluginContext.moveBody(function, this)
        }

        // 1b. Materialize `@Trace(tags = [...])` as `this.tags.put(k, v)` at the start of the lambda.
        val tags = resolveTags(function)
        if (tags.isNotEmpty()) {
            val tagsGetter = spanTagsGetter
            val putFn = mutableMapPut
            val lambdaReceiver = lambda.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
            val lambdaBody = lambda.body as? IrBlockBody
            if (tagsGetter == null || putFn == null || lambdaReceiver == null || lambdaBody == null) {
                messageCollector.reportError(
                    function,
                    "log4k-compiler-plugin: could not apply @Trace tags (unresolved `Span.tags` / `MutableMap.put`).",
                )
                return
            }
            val tagBuilder = DeclarationIrBuilder(pluginContext, lambda.symbol)
            val tagStatements = tags.map { (key, value) ->
                putTag(tagBuilder, lambdaReceiver, tagsGetter, putFn, key, value)
            }
            lambdaBody.statements.addAll(0, tagStatements)
        }

        // 2. `ctx.span<returnType>("name", <lambda>)`.
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val functionType = pluginContext.irBuiltIns.functionN(1).symbol.typeWith(spanLocalType, returnType)
        val lambdaExpression = Log4kIrFunctionExpression(
            startOffset = function.startOffset,
            endOffset = function.endOffset,
            type = functionType,
            origin = IrStatementOrigin.LAMBDA,
            function = lambda,
        )

        val call = builder.irCall(spanFn, returnType, listOf(returnType)).apply {
            arguments[dispatchParam] = builder.irGetObjectValue(dispatchParam.type, dispatchClass)
            arguments[extensionParam] = builder.irGet(contextParam)
            arguments[nameParam] = builder.irString(spanName)
            arguments[fParam] = lambdaExpression
        }

        // 3. Replace the original body with `return ctx.span(...) { ... }`.
        function.body = builder.irBlockBody { +irReturn(call) }
    }

    private fun resolveSpanName(function: IrFunction): String {
        val annotation = function.getAnnotation(TRACE_ANNOTATION)
        val configured = (annotation?.arguments?.getOrNull(0) as? IrConst)?.value as? String
        if (!configured.isNullOrBlank()) return configured

        // Default: "ClassName.functionName" (or just "functionName" for top-level functions).
        return function.qualifiedName()
    }

    /** Reads the `@Trace(tags = [Tag(k, v), …])` array into (key, value) pairs. */
    private fun resolveTags(function: IrFunction): List<Pair<String, String>> {
        // Class-level tags come first so a function's own tags override them (later puts win).
        val classTags = tagsOf(function.parentClassOrNull?.getAnnotation(TRACE_ANNOTATION))
        val functionTags = tagsOf(function.getAnnotation(TRACE_ANNOTATION))
        return classTags + functionTags
    }

    private fun tagsOf(annotation: IrConstructorCall?): List<Pair<String, String>> {
        val tagsArg = annotation?.arguments?.getOrNull(1) as? IrVararg ?: return emptyList()
        return tagsArg.elements.mapNotNull { element ->
            val tag = element as? IrConstructorCall ?: return@mapNotNull null
            val key = (tag.arguments.getOrNull(0) as? IrConst)?.value as? String
            val value = (tag.arguments.getOrNull(1) as? IrConst)?.value as? String
            if (key != null && value != null) key to value else null
        }
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
            tagsGetter.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
                ?.let { arguments[it] = builder.irGet(receiver) }
        }
        return builder.irCall(put).apply {
            val params = put.owner.parameters
            params.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }?.let { arguments[it] = getTags }
            val regular = params.filter { it.kind == IrParameterKind.Regular }
            regular.getOrNull(0)?.let { arguments[it] = builder.irString(key) }
            regular.getOrNull(1)?.let { arguments[it] = builder.irString(value) }
        }
    }

    companion object {
        private val TRACE_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Trace")
        private val NO_TRACE_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.NoTrace")
    }
}
