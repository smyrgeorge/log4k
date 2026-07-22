package io.github.smyrgeorge.log4k.compiler.timed

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
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
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
 * `io.github.smyrgeorge.log4k.annotation.Timed` so that each invocation records call/error counters
 * and a duration histogram.
 *
 * Given:
 * ```kotlin
 * class UserService {
 *     @Timed
 *     fun compute(x: Int): Int { /* body */ }
 * }
 * ```
 *
 * the body is replaced with (conceptually):
 * ```kotlin
 * fun compute(x: Int): Int =
 *     Meter.of(this::class).timed("UserService.compute").measure { /* body */ }
 * ```
 *
 * `Meter.timed` caches its instrument bundle by name, so the instruments are created once; and
 * `Meter.Timed.measure` is `inline`, so both regular and `suspend` functions work: the moved body is
 * placed in an inline lambda and therefore keeps its original suspension context.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class TimedIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // `Meter.Companion.of(KClass<*>)` — used to obtain the meter via `Meter.of(this::class)`.
    private val meterOfFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("Meter.Companion"), false), Name.identifier("of")),
    ).firstOrNull { symbol ->
        val regular = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
        regular.size == 1 && regular[0].type.classOrNull == pluginContext.irBuiltIns.kClassClass
    }

    // `Meter.timed(name): Meter.Timed` — returns the (cached) instrument bundle for a base name.
    private val meterTimedFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("Meter"), false), Name.identifier("timed")),
    ).firstOrNull { symbol ->
        symbol.owner.parameters.count { it.kind == IrParameterKind.Regular } == 1
    }

    // `Meter.Timed.measure(f)` — the inline helper that records the metrics around the body.
    private val measureFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(ClassId(LOG4K_PACKAGE, FqName("Meter.Timed"), false), Name.identifier("measure")),
    ).firstOrNull()

    // The log4k metering API must be on the classpath for the plugin to do anything.
    val isReady: Boolean =
        meterOfFunction != null && meterTimedFunction != null && measureFunction != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        if (shouldInstrument(declaration)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    private fun shouldInstrument(function: IrFunction): Boolean {
        if (function.body == null) return false

        // Explicit @Timed on the function.
        if (function.hasAnnotation(TIMED_ANNOTATION)) return true

        // Class-level @Timed: instrument eligible public member functions.
        val enclosingClass = function.parentClassOrNull ?: return false
        if (!enclosingClass.hasAnnotation(TIMED_ANNOTATION)) return false
        return function.isClassLevelEligible()
    }

    private fun instrument(function: IrFunction) {
        val meterOfFn = meterOfFunction ?: return
        val timedFn = meterTimedFunction ?: return
        val measureFn = measureFunction ?: return

        val thisParam = function.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
        if (thisParam == null) {
            messageCollector.reportError(
                function,
                "@Timed function '${function.name.asString()}' must be a member of a class or object " +
                        "so `Meter.of(this::class)` is available.",
            )
            return
        }

        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` (a plain `() -> T`).
        val lambda = pluginContext.buildInlineLambda(function, returnType)

        // 2. `Meter.of(this::class).timed("name").measure<returnType> { <lambda> }`.
        val meterOf = builder.irOfThisClass(pluginContext, meterOfFn, thisParam)
        val timed = builder.irCall(timedFn).apply {
            timedFn.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
                ?.let { arguments[it] = meterOf }
            timedFn.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular }
                ?.let { arguments[it] = builder.irString(resolveName(function)) }
        }

        val functionType = pluginContext.irBuiltIns.functionN(0).symbol.typeWith(returnType)
        val lambdaExpression = Log4kIrFunctionExpression(
            startOffset = function.startOffset,
            endOffset = function.endOffset,
            type = functionType,
            origin = IrStatementOrigin.LAMBDA,
            function = lambda,
        )

        val measureParams = measureFn.owner.parameters
        val measureDispatch = measureParams.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
        val measureF = measureParams.firstOrNull { it.kind == IrParameterKind.Regular }
        if (measureDispatch == null || measureF == null) {
            messageCollector.reportError(
                function,
                "log4k-compiler-plugin: could not resolve the expected `Meter.Timed.measure` signature — " +
                        "the plugin is incompatible with this version of log4k.",
            )
            return
        }

        val call = builder.irCall(measureFn, returnType, listOf(returnType)).apply {
            arguments[measureDispatch] = timed
            arguments[measureF] = lambdaExpression
        }

        // 3. Replace the original body with `return Meter.of(this::class).timed(...).measure { ... }`.
        function.body = builder.irBlockBody { +irReturn(call) }
    }

    /** The base metric name: the function's own `@Timed(name)` if non-blank, else "ClassName.functionName". */
    private fun resolveName(function: IrFunction): String {
        val annotation = function.getAnnotation(TIMED_ANNOTATION)
        val configured = (annotation?.arguments?.getOrNull(0) as? IrConst)?.value as? String
        if (!configured.isNullOrBlank()) return configured
        return function.qualifiedName()
    }

    companion object {
        private val TIMED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Timed")
    }
}
