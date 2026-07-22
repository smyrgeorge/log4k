package io.github.smyrgeorge.log4k.compiler.logged

import io.github.smyrgeorge.log4k.compiler.ir.Log4kIrFunctionExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.LOG4K_PACKAGE
import io.github.smyrgeorge.log4k.compiler.ir.utils.OfThisClassField
import io.github.smyrgeorge.log4k.compiler.ir.utils.buildInlineLambda
import io.github.smyrgeorge.log4k.compiler.ir.utils.dispatchReceiverParam
import io.github.smyrgeorge.log4k.compiler.ir.utils.isClassLevelEligible
import io.github.smyrgeorge.log4k.compiler.ir.utils.qualifiedName
import io.github.smyrgeorge.log4k.compiler.ir.utils.receiverOrContextOf
import io.github.smyrgeorge.log4k.compiler.ir.utils.regularParams
import io.github.smyrgeorge.log4k.compiler.ir.utils.reportError
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
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

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
 * fun compute(x: Int): Int = log.logged(Level.INFO, span = null, "UserService.compute", "x=$x") {
 *     /* body */
 * }
 * ```
 *
 * `Logger.logged` is `inline`, so both regular and `suspend` functions work: the moved body is
 * placed in an inline lambda and therefore keeps its original suspension context. The `Logger` is
 * resolved by [OfThisClassField]: a log4k `log: Logger` member is reused; otherwise (or if `log` is a
 * foreign type such as `org.slf4j.Logger`) `private val _log_ = Logger.of(this::class)` is
 * synthesized. A span is attached to the log lines when one is in scope: a `TracingContext`
 * parameter/receiver (its `currentOrNull()`), else a `TracingEvent.Span` parameter/receiver directly,
 * else none.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class LoggedIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // The `inline fun <T> Logger.logged(level, span, name, args, f)` member helper.
    private val loggedFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("Logger"), false), Name.identifier("logged")),
    ).firstOrNull { symbol -> symbol.owner.regularParams().size == 5 }

    // Reuses a log4k `log: Logger` member, or synthesizes `private val _log_ = Logger.of(this::class)`.
    private val loggerField: OfThisClassField? =
        OfThisClassField.of(pluginContext, finder, messageCollector, "Logger", "@Logged", "log", "_log_")

    // `Level` enum + its entries, to materialize the `@Logged(level = …)` argument.
    private val levelClassSymbol: IrClassSymbol? =
        finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("Level")))
    private val levelEntries: Map<String, IrEnumEntry> = levelClassSymbol?.owner?.declarations
        ?.filterIsInstance<IrEnumEntry>()?.associateBy { it.name.asString() } ?: emptyMap()

    // `TracingContext` + `currentOrNull()` — used to correlate the logs with the active span.
    private val tracingContextSymbol: IrClassSymbol? =
        finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("TracingContext")))
    private val currentOrNullFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("TracingContext"), false), Name.identifier("currentOrNull")),
    ).firstOrNull()

    // `TracingEvent.Span` — a span in scope (e.g. a `Span.Local` receiver) is attached directly.
    private val spanClassSymbol: IrClassSymbol? =
        finder.findClass(ClassId(LOG4K_PACKAGE, FqName("TracingEvent.Span"), false))

    // The log4k logging API must be on the classpath for the plugin to do anything.
    val isReady: Boolean = loggedFunction != null && loggerField != null && levelClassSymbol != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (shouldInstrument(declaration)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    /** Attaches every synthesized `_log_` field to its class. Must run after the module transform. */
    fun commit() = loggerField?.commit()

    private fun shouldInstrument(function: IrFunction): Boolean {
        if (function.body == null) return false

        // Explicit @Logged on the function.
        if (function.hasAnnotation(LOGGED_ANNOTATION)) return true

        // Class-level @Logged: instrument eligible public member functions.
        val enclosingClass = function.parentClassOrNull ?: return false
        if (!enclosingClass.hasAnnotation(LOGGED_ANNOTATION)) return false
        return function.isClassLevelEligible()
    }

    private fun instrument(function: IrFunction) {
        val loggedFn = loggedFunction ?: return

        val dispatchParam = loggedFn.owner.dispatchReceiverParam()
        val regular = loggedFn.owner.regularParams()
        if (dispatchParam == null || regular.size != 5) {
            messageCollector.reportError(
                function,
                "log4k-compiler-plugin: could not resolve the expected `Logger.logged` signature — " +
                        "the plugin is incompatible with this version of log4k.",
            )
            return
        }
        val levelParam = regular[0]
        val spanParam = regular[1]
        val nameParam = regular[2]
        val argsParam = regular[3]
        val fParam = regular[4]

        // Resolve (or synthesize) the `log: Logger` to call `logged` on. Errors are reported inside.
        val loggerAccess = loggerField?.access(function) ?: return

        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` (a plain `() -> T`).
        val lambda = pluginContext.buildInlineLambda(function, returnType)

        // 2. `log.logged<returnType>(level, span, "name", "args", <lambda>)`.
        val functionType = pluginContext.irBuiltIns.functionN(0).symbol.typeWith(returnType)
        val lambdaExpression = Log4kIrFunctionExpression(
            startOffset = function.startOffset,
            endOffset = function.endOffset,
            type = functionType,
            origin = IrStatementOrigin.LAMBDA,
            function = lambda,
        )

        val call = builder.irCall(loggedFn, returnType, listOf(returnType)).apply {
            arguments[dispatchParam] = loggerAccess
            arguments[levelParam] = resolveLevel(function)
            arguments[spanParam] = buildSpan(builder, function, spanParam.type)
            arguments[nameParam] = builder.irString(function.qualifiedName())
            arguments[argsParam] = buildArgs(builder, function)
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
        val entry = levelEntries[name] ?: levelEntries.getValue("INFO")
        return IrGetEnumValueImpl(
            function.startOffset,
            function.endOffset,
            levelClassSymbol!!.defaultType,
            entry.symbol
        )
    }

    private fun levelName(annotation: IrConstructorCall?): String? {
        val arg = annotation?.arguments?.getOrNull(0) as? IrGetEnumValue ?: return null
        return arg.symbol.owner.name.asString()
    }

    /** Builds the `paramName=value, …` string rendered inside the entry log's parentheses. */
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
            concat.arguments.add(builder.irString("${param.name.asString()}="))
            concat.arguments.add(builder.irGet(param))
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
        val contextSymbol = tracingContextSymbol
        val currentFn = currentOrNullFunction
        if (contextSymbol != null && currentFn != null) {
            val contextParam = function.receiverOrContextOf(contextSymbol)
            if (contextParam != null) {
                return builder.irCall(currentFn).apply {
                    currentFn.owner.dispatchReceiverParam()?.let { arguments[it] = builder.irGet(contextParam) }
                }
            }
        }
        // 2. A TracingEvent.Span in scope (e.g. a `Span.Local` receiver) -> attach it directly.
        val spanSymbol = spanClassSymbol
        if (spanSymbol != null) {
            val spanParam = function.receiverOrContextOf(spanSymbol)
            if (spanParam != null) return builder.irGet(spanParam)
        }
        // 3. Nothing in scope.
        return builder.irNull(spanType)
    }

    companion object {
        private val LOGGED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Logged")
    }
}
