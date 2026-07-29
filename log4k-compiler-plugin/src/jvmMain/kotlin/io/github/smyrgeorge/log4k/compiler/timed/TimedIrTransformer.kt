package io.github.smyrgeorge.log4k.compiler.timed

import io.github.smyrgeorge.log4k.compiler.ir.utils.AnnotationTagsBuilder
import io.github.smyrgeorge.log4k.compiler.ir.utils.OfThisClassField
import io.github.smyrgeorge.log4k.compiler.ir.utils.buildInlineLambdaExpression
import io.github.smyrgeorge.log4k.compiler.ir.utils.dispatchReceiverParam
import io.github.smyrgeorge.log4k.compiler.ir.utils.findLog4kFunction
import io.github.smyrgeorge.log4k.compiler.ir.utils.instrumentationName
import io.github.smyrgeorge.log4k.compiler.ir.utils.isInstrumentationTarget
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
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.FqName

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
 * fun compute(x: Int): Int = meter.timed("UserService.compute").measure { /* body */ }
 * ```
 *
 * The `Meter` is resolved by [OfThisClassField]: a `meter: Meter` member is reused; otherwise
 * `private val _meter_ = Meter.of(this::class)` is synthesized (created once per class). `Meter.timed`
 * caches its instrument bundle by name, and `Meter.Timed.measure` is `inline`, so both regular and
 * `suspend` function work: the moved body is placed in an inline lambda and therefore keeps its
 * original suspension context.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class TimedIrTransformer(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    // Reuses a `meter: Meter` member, or synthesizes `private val _meter_ = Meter.of(this::class)`.
    private val meterField: OfThisClassField? =
        OfThisClassField.of(pluginContext, finder, messageCollector, "Meter", "@Timed", "meter", "_meter_")

    // `Meter.timed(name, vararg tags): Meter.Timed` — returns the (cached) instrument bundle.
    private val meterTimedFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Meter", "timed", regularParams = 2)

    // Materializes `@Timed(tags = [...])` as the `vararg tags: Pair<String, Any>` metric dimensions.
    private val tagsBuilder = AnnotationTagsBuilder(pluginContext, finder, messageCollector)

    // `Meter.Timed.measure(f)` — the inline helper that records the metrics around the body.
    private val measureFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Meter.Timed", "measure", regularParams = 1)

    // The log4k metering API must be on the classpath for the plugin to do anything.
    val isReady: Boolean =
        meterField != null && meterTimedFunction != null && measureFunction != null

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        // @NoTime on the function — or on its class (a per-class kill switch) — overrides any @Timed.
        if (declaration.isInstrumentationTarget(TIMED_ANNOTATION, NO_TIME_ANNOTATION)) instrument(declaration)
        return super.visitFunctionNew(declaration)
    }

    /** Attaches every synthesized `_meter_` field to its class. Must run after the module transform. */
    fun commit() = meterField?.commit()

    private fun instrument(function: IrFunction) {
        val timedFn = meterTimedFunction ?: return
        val measureFn = measureFunction ?: return

        // Resolve (or synthesize) the `meter: Meter` to time against. Errors are reported inside.
        val meterAccess = meterField?.access(function) ?: return

        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val returnType = function.returnType

        // 1. Build the inline lambda `{ <original body> }` (a plain `() -> T`).
        val lambdaExpression = pluginContext.buildInlineLambdaExpression(function, returnType)

        // 2. `meter.timed("name", *tags).measure<returnType> { <lambda> }`.
        val timedRegular = timedFn.owner.regularParams()
        val timed = builder.irCall(timedFn).apply {
            timedFn.owner.dispatchReceiverParam()?.let { arguments[it] = meterAccess }
            timedRegular.getOrNull(0)?.let {
                arguments[it] = builder.irString(function.instrumentationName(TIMED_ANNOTATION))
            }
            timedRegular.getOrNull(1)?.let {
                arguments[it] = tagsBuilder.buildVararg(
                    builder = builder,
                    function = function,
                    tags = function.resolveTags(TIMED_ANNOTATION),
                    tagsParam = it,
                    annotationName = "@Timed"
                )
            }
        }

        val measureDispatch = measureFn.owner.dispatchReceiverParam()
        val measureF = measureFn.owner.regularParams().firstOrNull()
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

        // 3. Replace the original body with `return meter.timed(...).measure { ... }`.
        function.body = builder.irBlockBody { +irReturn(call) }
    }

    companion object {
        private val TIMED_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.Timed")
        private val NO_TIME_ANNOTATION = FqName("io.github.smyrgeorge.log4k.annotation.NoTime")
    }
}
