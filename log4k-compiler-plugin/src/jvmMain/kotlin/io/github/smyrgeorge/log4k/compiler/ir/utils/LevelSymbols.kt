package io.github.smyrgeorge.log4k.compiler.ir.utils

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * The log4k `Level` enum, resolved once per module: its class symbol plus the entries by name.
 * Shared by the transformers that materialize `Level` arguments
 * ([io.github.smyrgeorge.log4k.compiler.logged.LoggedIrTransformer] and
 * [io.github.smyrgeorge.log4k.compiler.callsite.CallSiteIrTransformer]).
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class LevelSymbols private constructor(
    private val clazz: IrClassSymbol,
    val entries: Map<String, IrEnumEntry>,
) {
    /** Builds a `Level.<name>` access anchored at [startOffset]..[endOffset], or `null` for an unknown name. */
    fun get(name: String, startOffset: Int, endOffset: Int): IrExpression? =
        entries[name]?.let { IrGetEnumValueImpl(startOffset, endOffset, clazz.defaultType, it.symbol) }

    companion object {
        /** Resolves the `Level` enum, or `null` when log4k is not on the classpath. */
        fun of(finder: DeclarationFinder): LevelSymbols? {
            val clazz = finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("Level"))) ?: return null
            val entries = clazz.owner.declarations
                .filterIsInstance<IrEnumEntry>()
                .associateBy { it.name.asString() }
            return LevelSymbols(clazz, entries)
        }
    }
}
