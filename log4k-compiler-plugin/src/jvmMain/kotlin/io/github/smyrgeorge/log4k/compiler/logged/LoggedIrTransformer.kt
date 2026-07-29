package io.github.smyrgeorge.log4k.compiler.logged

import io.github.smyrgeorge.log4k.compiler.ir.utils.AnnotationTagsBuilder
import io.github.smyrgeorge.log4k.compiler.ir.utils.LevelSymbols
import io.github.smyrgeorge.log4k.compiler.ir.utils.OfThisClassField
import io.github.smyrgeorge.log4k.compiler.ir.utils.TracingSymbols
import io.github.smyrgeorge.log4k.compiler.ir.utils.buildInlineLambdaExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.dispatchReceiverParam
import io.github.smyrgeorge.log4k.compiler.ir.utils.findLog4kFunction
import io.github.smyrgeorge.log4k.compiler.ir.utils.findSourceLocationConstructor
import io.github.smyrgeorge.log4k.compiler.ir.utils.irSourceLocation
import io.github.smyrgeorge.log4k.compiler.ir.utils.isInstrumentationTarget
import io.github.smyrgeorge.log4k.compiler.ir.utils.qualifiedName
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
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.FqName

/**
 * Rewrites the body of every function annotated with
 * `io.github.smyrgeorge.log4k.annotation.Logged` so that it is executed with entry/exit logging.
 *
 * Given:
 * ```kotlin
 * class UserService {
 *     private val log = Logger.of(this::class)
 *
 *     @Logged
 *     fun compute(x: Int): Int { /* body */ }
 * }
 * ```
 *
 * the body is replaced with (conceptually):
 * ```kotlin
 * fun compute(x: Int): Int =
 *     log.logged(Level.INFO, span = null, tags = emptyMap(), "UserService.compute", "x=$x",
 *                SourceLocation("UserService.kt", 6, "UserService.compute")) {
 *         /* body */
 *     }
 * ```
 *
 * The `SourceLocation` describes the instrumented function's **declaration** (its file, line, and
 * `ClassName.functionName`) — also when the annotation sits on the class — so every emitted line is
 * attributed to the annotated function.
 *
 * `@Logged(tags = [Tag(k, v), …])` is materialized as the `tags` map argument (class-level tags are
 * added first, so a function's own tag with the same key wins).
 *
 * A parameter annotated with `io.github.smyrgeorge.log4k.annotation.Masked` is rendered in the
 * entry line as the literal `paramName=<MASKED>` instead of its value.
 *
 * `Logger.logged` is `inline`, so both regular and `suspend` functions work: the moved body is
 * placed in an inline lambda and therefore keeps its original suspension context. The `Logger` is
 * resolved by [OfThisClassField]: a log4k `log: Logger` member is reused, else the class's single
 * `Logger`-typed property (whatever its name); otherwise (e.g. `log` is a foreign type such as
 * `org.slf4j.Logger` and no other log4k `Logger` is declared) `private val _log_ =
 * Logger.of(this::class)` is synthesized. A span is attached to the log lines when one is in scope: a `TracingContext`
 * parameter/receiver (its `currentOrNull()`), else a `TracingEvent.Span` parameter/receiver directly,
 * else none.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class LoggedIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // The `inline fun <T> Logger.logged(level, span, tags, name, args[, callSite], f)` member helper —
    // the call-site-aware 7-parameter overload is preferred; the 6-parameter one is the fallback for
    // an older log4k runtime.
    private val loggedFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Logger", "logged", regularParams = 7)
            ?: finder.findLog4kFunction("Logger", "logged", regularParams = 6)

    // `SourceLocation(file, line, function)` — describes the instrumented function's declaration on the
    // emitted lines. Absent on an older log4k runtime; the lines then carry no location.
    private val sourceLocationConstructor: IrConstructorSymbol? = finder.findSourceLocationConstructor()

    // Materializes `@Logged(tags = [...])` as the `tags: Map<String, Any>` argument.
    private val tagsBuilder = AnnotationTagsBuilder(pluginContext, finder, messageCollector)

    // Reuses a log4k `log: Logger` member (else the class's single `Logger`-typed property), or
    // synthesizes `private val _log_ = Logger.of(this::class)`.
    private val loggerField: OfThisClassField? =
        OfThisClassField.of(pluginContext, finder, messageCollector, "Logger", "@Logged", "log", "_log_")

    // The `Level` enum, to materialize the `@Logged(level = …)` argument.
    private val levels: LevelSymbols? = LevelSymbols.of(finder)

    // `TracingContext` + `currentOrNull()`, and `TracingEvent.Span` — used to correlate the
    // emitted log lines with the active span (see [buildSpan]).
    private val tracing = TracingSymbols.of(finder)

    // The log4k logging API must be on the classpath for the plugin to do anything.
    val isReady: Boolean = loggedFunction != null && loggerField != null && levels != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        // @NoLog on the function — or on its class (a per-class kill switch) — overrides any @Logged.
        if (declaration.isInstrumentationTarget(LOGGED_ANNOTATION, NO_LOG_ANNOTATION)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    /** Attaches every synthesized `_log_` field to its class. Must run after the module transform. */
    fun commit() = loggerField?.commit()

    private fun instrument(function: IrFunction) {
        val loggedFn = loggedFunction ?: return

        val dispatchParam = loggedFn.owner.dispatchReceiverParam()
        val regular = loggedFn.owner.regularParams()
        if (dispatchParam == null || regular.size !in 6..7) {
            messageCollector.reportError(
                function,
                "log4k-compiler-plugin: could not resolve the expected `Logger.logged` signature — " +
                        "the plugin is incompatible with this version of log4k.",
            )
            return
        }
        val levelParam = regular[0]
        val spanParam = regular[1]
        val tagsParam = regular[2]
        val nameParam = regular[3]
        val argsParam = regular[4]
        val callSiteParam = if (regular.size == 7) regular[5] else null
        val fParam = regular.last()

        // Resolve (or synthesize) the `log: Logger` to call `logged` on. Errors are reported inside.
        val loggerAccess = loggerField?.access(function) ?: return

        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` (a plain `() -> T`).
        val lambdaExpression = pluginContext.buildInlineLambdaExpression(function, returnType)

        // 2. `log.logged<returnType>(level, span, "name", "args", <lambda>)`.
        val tags = function.resolveTags(LOGGED_ANNOTATION)
        val tagsArg = tagsBuilder.buildMap(builder, function, tags, tagsParam.type, "@Logged") ?: return
        val call = builder.irCall(loggedFn, returnType, listOf(returnType)).apply {
            arguments[dispatchParam] = loggerAccess
            arguments[levelParam] = resolveLevel(function)
            arguments[spanParam] = buildSpan(builder, function, spanParam.type)
            arguments[tagsParam] = tagsArg
            arguments[nameParam] = builder.irString(function.qualifiedName())
            arguments[argsParam] = buildArgs(builder, function)
            callSiteParam?.let { param ->
                // The **declaration** of the instrumented function — the line of the annotated
                // function itself (also when the annotation sits on the class) — so every emitted
                // line is attributed to it. Falls back to `null` for synthetic declarations.
                arguments[param] = sourceLocationConstructor?.let {
                    builder.irSourceLocation(it, currentFile, function.startOffset, function.qualifiedName())
                } ?: builder.irNull(param.type)
            }
            arguments[fParam] = lambdaExpression
        }

        // 3. Replace the original body with `return log.logged(...) { ... }`.
        function.body = builder.irBlockBody { +irReturn(call) }
    }

    /** The entry/exit log level: the function's own `@Logged(level)`, else the class', else INFO. */
    private fun resolveLevel(function: IrFunction): IrExpression {
        val name = levelName(function.getAnnotation(LOGGED_ANNOTATION))
            ?: levelName(function.parentClassOrNull?.getAnnotation(LOGGED_ANNOTATION))
            ?: "INFO"
        val levels = levels
            ?: error("log4k-compiler-plugin: `Level` not resolved — guarded by `isReady`.")
        return levels.get(name, function.startOffset, function.endOffset)
            ?: levels.get("INFO", function.startOffset, function.endOffset)
            ?: error("log4k-compiler-plugin: the `Level` enum has no INFO entry.")
    }

    private fun levelName(annotation: IrConstructorCall?): String? {
        val arg = annotation?.arguments?.getOrNull(0) as? IrGetEnumValue ?: return null
        return arg.symbol.owner.name.asString()
    }

    /**
     * Builds the `paramName=value, …` string rendered inside the entry log's parentheses.
     *
     * A parameter annotated with `@Masked` is rendered as the literal `paramName=<MASKED>` instead —
     * its value is never read (and thus never `toString()`ed), so it cannot leak into the logs.
     */
    private fun buildArgs(builder: DeclarationIrBuilder, function: IrFunction): IrExpression {
        val valueParams = function.regularParams()
        if (valueParams.isEmpty()) return builder.irString("")
        val concat = IrStringConcatenationImpl(
            function.startOffset,
            function.endOffset,
            pluginContext.irBuiltIns.stringType,
        )
        valueParams.forEachIndexed { index, param ->
            if (index > 0) concat.arguments.add(builder.irString(", "))
            if (param.hasAnnotation(MASKED_ANNOTATION)) {
                concat.arguments.add(builder.irString("${param.name.asString()}=$MASKED_VALUE"))
            } else {
                concat.arguments.add(builder.irString("${param.name.asString()}="))
                concat.arguments.add(builder.irGet(param))
            }
        }
        return concat
    }

    /**
     * The span to attach to the emitted log lines:
     * 1. `ctx.currentOrNull()` when a `TracingContext` is in scope (context parameter or receiver);
     * 2. otherwise a `TracingEvent.Span` in scope (e.g. a `Span.Local` receiver), used directly;
     * 3. otherwise `null`.
     */
    private fun buildSpan(
        builder: DeclarationIrBuilder,
        function: IrFunction,
        spanType: IrType,
    ): IrExpression {
        // 1. A TracingContext in scope -> its current span.
        val contextParam = tracing.tracingContext?.let { function.receiverOrContextOf(it) }
        if (contextParam != null) {
            tracing.irCurrentOrNull(builder, builder.irGet(contextParam))?.let { return it }
        }
        // 2. A TracingEvent.Span in scope (e.g., a `Span.Local` receiver) -> attach it directly.
        val spanParam = tracing.span?.let { function.receiverOrContextOf(it) }
        if (spanParam != null) return builder.irGet(spanParam)
        // 3. Nothing in scope.
        return builder.irNull(spanType)
    }

    companion object {
        private val LOGGED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Logged")
        private val NO_LOG_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.NoLog")
        private val MASKED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Masked")

        /** Rendered in place of a `@Masked` parameter's value in the entry log line. */
        private const val MASKED_VALUE = "<MASKED>"
    }
}
