package io.github.smyrgeorge.log4k.compiler.ir.utils

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Resolves — or synthesizes — a per-class member holding an instance of [typeSymbol] obtained via a
 * companion `of(KClass<*>)` factory (e.g. `Logger.of(this::class)` / `Meter.of(this::class)`).
 *
 * [access] reuses a member named [memberName] of the target type if the class declares one, otherwise
 * it synthesizes `private val [syntheticName] = <Type>.of(this::class)` (created once per class). A
 * [memberName] member of a foreign type (e.g. `org.slf4j.Logger` when we want a log4k `Logger`) is
 * ignored — the synthetic name is distinct, so it never clashes.
 *
 * Synthesized fields are collected during the module traversal and only attached to their classes by
 * [commit] afterwards, so a class's declaration list is never mutated while it is being iterated.
 *
 * Shared by [io.github.smyrgeorge.log4k.compiler.logged.LoggedIrTransformer] (`log` / `_log_`) and
 * [io.github.smyrgeorge.log4k.compiler.timed.TimedIrTransformer] (`meter` / `_meter_`).
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class OfThisClassField(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val typeSymbol: IrClassSymbol,
    private val ofFunction: IrSimpleFunctionSymbol,
    private val annotation: String,
    private val memberName: String,
    private val syntheticName: String,
) {
    // Fields synthesized during traversal; attached to their classes by [commit] afterwards.
    private val created = mutableMapOf<IrClass, IrField>()

    /**
     * Returns an expression that yields the [typeSymbol] instance for [function] (reused member or
     * synthesized field), or `null` (after reporting an error) when [function] has no enclosing class
     * or dispatch receiver.
     */
    fun access(function: IrFunction): IrExpression? {
        val enclosingClass = function.parentClassOrNull ?: return messageCollector.reportError(
            function,
            "$annotation function '${function.name.asString()}' must be a member of a class or object.",
        )
        val thisParam = function.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
            ?: return messageCollector.reportError(
                function,
                "$annotation function '${function.name.asString()}' has no dispatch receiver.",
            )
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        // Reuse an existing `memberName` member of the target type.
        val existing = enclosingClass.properties.firstOrNull { it.name.asString() == memberName }
        if (existing != null) {
            val getter = existing.getter
            if (getter != null && getter.returnType.isSubtypeOfClass(typeSymbol)) {
                return builder.irCall(getter.symbol).apply {
                    getter.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
                        ?.let { arguments[it] = builder.irGet(thisParam) }
                }
            }
            val backing = existing.backingField
            if (backing != null && backing.type.isSubtypeOfClass(typeSymbol)) {
                return builder.irGetField(builder.irGet(thisParam), backing)
            }
            // The member exists but is a foreign type; fall through and synthesize our own.
        }

        return builder.irGetField(builder.irGet(thisParam), getOrCreate(enclosingClass))
    }

    private fun getOrCreate(clazz: IrClass): IrField = created.getOrPut(clazz) {
        pluginContext.irFactory.buildField {
            name = Name.identifier(syntheticName)
            type = typeSymbol.defaultType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            origin = IrDeclarationOrigin.DEFINED
        }.apply {
            parent = clazz
            val initBuilder = DeclarationIrBuilder(pluginContext, symbol)
            initializer = pluginContext.irFactory.createExpressionBody(
                clazz.startOffset,
                clazz.endOffset,
                initBuilder.irOfThisClass(pluginContext, ofFunction, clazz.thisReceiver!!),
            )
        }
    }

    /** Attaches every synthesized field to its class. Must run after the module transform. */
    fun commit() {
        created.forEach { (clazz, field) -> clazz.declarations.add(field) }
        created.clear()
    }

    companion object {
        /**
         * Builds an [OfThisClassField] for the log4k type named [typeName] (e.g. `"Logger"` / `"Meter"`)
         * whose companion exposes an `of(KClass<*>)` factory, or `null` if either can't be resolved.
         */
        fun of(
            pluginContext: IrPluginContext,
            finder: DeclarationFinder,
            messageCollector: MessageCollector,
            typeName: String,
            annotation: String,
            memberName: String,
            syntheticName: String,
        ): OfThisClassField? {
            val typeSymbol = finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier(typeName))) ?: return null
            val ofFunction = finder.findFunctions(
                CallableId(ClassId(LOG4K_PACKAGE, FqName("$typeName.Companion"), false), Name.identifier("of")),
            ).firstOrNull { symbol ->
                val regular = symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }
                regular.size == 1 && regular[0].type.classOrNull == pluginContext.irBuiltIns.kClassClass
            } ?: return null
            return OfThisClassField(
                pluginContext, messageCollector, typeSymbol, ofFunction, annotation, memberName, syntheticName,
            )
        }
    }
}
