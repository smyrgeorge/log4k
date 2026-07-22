package io.github.smyrgeorge.log4k.compiler.logged

import io.github.smyrgeorge.log4k.compiler.ir.Log4kIrFunctionExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.LOG4K_PACKAGE
import io.github.smyrgeorge.log4k.compiler.ir.utils.buildInlineLambda
import io.github.smyrgeorge.log4k.compiler.ir.utils.irOfThisClass
import io.github.smyrgeorge.log4k.compiler.ir.utils.isClassLevelEligible
import io.github.smyrgeorge.log4k.compiler.ir.utils.qualifiedName
import io.github.smyrgeorge.log4k.compiler.ir.utils.reportError
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
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
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.properties
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
 * placed in an inline lambda and therefore keeps its original suspension context. If the class does
 * not declare a log4k `log: Logger` (or its `log` is a foreign type such as `org.slf4j.Logger`),
 * `private val _log_ = Logger.of(this::class)` is synthesized. If the function declares a
 * `TracingContext` context parameter, the current span is resolved from it and attached to every
 * emitted log line.
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
    ).firstOrNull { symbol ->
        symbol.owner.parameters.count { it.kind == IrParameterKind.Regular } == 5
    }

    // `Logger` — the dispatch receiver of `logged` and the type of the `log` field.
    private val loggerClassSymbol: IrClassSymbol? =
        finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("Logger")))

    // `Logger.Companion.of(KClass<*>)` — used to synthesize `Logger.of(this::class)`.
    private val loggerOfFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("Logger.Companion"), false), Name.identifier("of")),
    ).firstOrNull { symbol ->
        val regular = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
        regular.size == 1 && regular[0].type.classOrNull == pluginContext.irBuiltIns.kClassClass
    }

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

    // Loggers synthesized during traversal; added to their classes after the module transform so we
    // never mutate a class's declaration list while it is being iterated.
    private val createdLoggerFields = mutableMapOf<IrClass, IrField>()

    // The log4k logging API must be on the classpath for the plugin to do anything.
    val isReady: Boolean =
        loggedFunction != null && loggerClassSymbol != null && loggerOfFunction != null && levelClassSymbol != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (shouldInstrument(declaration)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    /** Adds every synthesized `log` field to its class. Must run after the module transform. */
    fun commitCreatedLoggers() {
        createdLoggerFields.forEach { (clazz, field) -> clazz.declarations.add(field) }
        createdLoggerFields.clear()
    }

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

        val params = loggedFn.owner.parameters
        val dispatchParam = params.singleOrNull { it.kind == IrParameterKind.DispatchReceiver }
        val regular = params.filter { it.kind == IrParameterKind.Regular }
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
        val loggerAccess = findOrCreateLogger(function) ?: return

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

    /**
     * Resolves the log4k `io.github.smyrgeorge.log4k.Logger` for [function]: reuses a `log: Logger`
     * property declared on the enclosing class, otherwise synthesizes
     * `private val _log_ = Logger.of(this::class)`. A `log` member of a foreign type (e.g.
     * `org.slf4j.Logger`) is ignored — the synthesized field uses a distinct name so it never clashes.
     * Returns an expression yielding the logger, or `null` (after reporting an error) when [function]
     * has no enclosing class / dispatch receiver.
     */
    private fun findOrCreateLogger(function: IrFunction): IrExpression? {
        val loggerSymbol = loggerClassSymbol ?: return null
        val enclosingClass = function.parentClassOrNull ?: return messageCollector.reportError(
            function,
            "@Logged function '${function.name.asString()}' must be a member of a class or object " +
                    "so a `log: Logger` is available.",
        )
        val thisParam = function.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
            ?: return messageCollector.reportError(
                function,
                "@Logged function '${function.name.asString()}' has no dispatch receiver to read `log` from.",
            )
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        // Reuse an existing `log` member on the enclosing class.
        val existing = enclosingClass.properties.firstOrNull { it.name.asString() == "log" }
        if (existing != null) {
            val getter = existing.getter
            if (getter != null && getter.returnType.isSubtypeOfClass(loggerSymbol)) {
                return builder.irCall(getter.symbol).apply {
                    getter.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
                        ?.let { arguments[it] = builder.irGet(thisParam) }
                }
            }
            val backing = existing.backingField
            if (backing != null && backing.type.isSubtypeOfClass(loggerSymbol)) {
                return builder.irGetField(builder.irGet(thisParam), backing)
            }
            // A `log` member exists but it is not a log4k Logger (e.g. `org.slf4j.Logger`); fall through
            // and synthesize our own logger under a distinct, non-clashing name.
        }

        // Synthesize `private val _log_ = Logger.of(this::class)` (once per class).
        val field = getOrCreateLoggerField(enclosingClass)
        return builder.irGetField(builder.irGet(thisParam), field)
    }

    private fun getOrCreateLoggerField(clazz: IrClass): IrField = createdLoggerFields.getOrPut(clazz) {
        val loggerType = loggerClassSymbol!!.defaultType
        pluginContext.irFactory.buildField {
            name = Name.identifier(SYNTHETIC_LOGGER_NAME)
            type = loggerType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            origin = IrDeclarationOrigin.DEFINED
        }.apply {
            parent = clazz
            val initBuilder = DeclarationIrBuilder(pluginContext, symbol)
            initializer = pluginContext.irFactory.createExpressionBody(
                clazz.startOffset,
                clazz.endOffset,
                initBuilder.irOfThisClass(pluginContext, loggerOfFunction!!, clazz.thisReceiver!!),
            )
        }
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
        val valueParams = function.parameters.filter { it.kind == IrParameterKind.Regular }
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

    /** `ctx.currentOrNull()` when the function has a `TracingContext` context parameter, else `null`. */
    private fun buildSpan(
        builder: DeclarationIrBuilder,
        function: IrFunction,
        spanType: IrType,
    ): IrExpression {
        val contextSymbol = tracingContextSymbol
        val currentFn = currentOrNullFunction
        if (contextSymbol != null && currentFn != null) {
            val contextParam = function.parameters.firstOrNull {
                it.kind == IrParameterKind.Context && it.type.isSubtypeOfClass(contextSymbol)
            }
            if (contextParam != null) {
                return builder.irCall(currentFn).apply {
                    currentFn.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
                        ?.let { arguments[it] = builder.irGet(contextParam) }
                }
            }
        }
        return builder.irNull(spanType)
    }

    companion object {
        // Name of the synthesized logger field. Deliberately not `log`, so it never clashes with a
        // user-declared `log` member of a foreign type (e.g. `org.slf4j.Logger`).
        private const val SYNTHETIC_LOGGER_NAME = "_log_"
        private val LOGGED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Logged")
    }
}
