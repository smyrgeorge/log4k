package io.github.smyrgeorge.log4k.compiler.ir.utils

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * The log4k tracing symbols the transformers correlate logs and spans with, resolved once per
 * module: `TracingContext` (plus its `currentOrNull()`) and `TracingEvent.Span`. Each is `null`
 * when the runtime on the classpath predates it — callers degrade gracefully.
 *
 * Shared by [io.github.smyrgeorge.log4k.compiler.callsite.CallSiteIrTransformer],
 * [io.github.smyrgeorge.log4k.compiler.logged.LoggedIrTransformer] and
 * [io.github.smyrgeorge.log4k.compiler.trace.TraceIrTransformer].
 */
class TracingSymbols private constructor(
    /** `TracingContext` — a context parameter/receiver of this type provides the active span. */
    val tracingContext: IrClassSymbol?,
    /** `TracingContext.currentOrNull()` — the active span of a context, or `null`. */
    val currentOrNull: IrSimpleFunctionSymbol?,
    /** `TracingEvent.Span` — a span in scope, attached or used as a parent directly. */
    val span: IrClassSymbol?,
) {
    /**
     * Builds `<receiver>.currentOrNull()` — the active span of the `TracingContext` yielded by
     * [receiver] — or `null` when [currentOrNull] is unresolved.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun irCurrentOrNull(builder: IrBuilderWithScope, receiver: IrExpression): IrExpression? {
        val fn = currentOrNull ?: return null
        return builder.irCall(fn).apply {
            fn.owner.dispatchReceiverParam()?.let { arguments[it] = receiver }
        }
    }

    companion object {
        /** Resolves whatever tracing symbols the log4k runtime on the classpath provides. */
        fun of(finder: DeclarationFinder): TracingSymbols = TracingSymbols(
            tracingContext = finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("TracingContext"))),
            currentOrNull = finder.findFunctions(
                CallableId(ClassId(LOG4K_PACKAGE, Name.identifier("TracingContext")), Name.identifier("currentOrNull")),
            ).firstOrNull(),
            span = finder.findClass(ClassId(LOG4K_PACKAGE, FqName("TracingEvent.Span"), false)),
        )
    }
}
