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
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
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
 * [access] resolves the member in three steps:
 * 1. a member named [memberName] (e.g. `log`) is reused when its type is the target type;
 * 2. otherwise the class's **single** property of the target type — whatever its name (e.g.
 *    `private val logger = Logger.of(this::class)`) — is reused; two or more such properties are
 *    ambiguous, so none is picked;
 * 3. otherwise `private val [syntheticName] = <Type>.of(this::class)` is synthesized (created once
 *    per class). A [memberName] member of a foreign type (e.g. `org.slf4j.Logger` when we want a
 *    log4k `Logger`) is never reused — the synthetic name is distinct, so it never clashes.
 *
 * Synthesized fields are collected during the module traversal and only attached to their classes by
 * [commit] afterwards, so a class's declaration list is never mutated while it is being iterated.
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
    // Fields synthesized during traversal; attached to their classes by [commit] afterward.
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
        val thisParam = function.dispatchReceiverParam()
            ?: return messageCollector.reportError(
                function,
                "$annotation function '${function.name.asString()}' has no dispatch receiver.",
            )
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)

        // 1. Reuse an existing `memberName` member of the target type.
        enclosingClass.properties.firstOrNull { it.name.asString() == memberName }
            ?.let { reuse(builder, thisParam, it) }
            ?.let { return it }

        // 2. No conventional member (or it is a foreign type): fall back to the class's single
        //    property of the target type, whatever its name. Two or more candidates are ambiguous —
        //    synthesize instead of guessing between them.
        enclosingClass.properties
            .mapNotNull { reuse(builder, thisParam, it) }
            .take(2).toList()
            .singleOrNull()
            ?.let { return it }

        // 3. Synthesize `private val <syntheticName> = <Type>.of(this::class)`.
        return builder.irGetField(builder.irGet(thisParam), getOrCreate(enclosingClass))
    }

    /**
     * Builds an access to [property] (a getter call, else a backing-field read) when its type is the
     * target type — or `null` when it is a foreign type. A member **extension** property (or one with
     * context parameters) is never reused: its getter needs receivers we cannot provide.
     */
    private fun reuse(
        builder: DeclarationIrBuilder,
        thisParam: IrValueParameter,
        property: IrProperty,
    ): IrExpression? {
        val getter = property.getter
        if (getter != null &&
            getter.returnType.isSubtypeOfClass(typeSymbol) &&
            getter.parameters.all { it.kind == IrParameterKind.DispatchReceiver }
        ) {
            return builder.irCall(getter.symbol).apply {
                getter.dispatchReceiverParam()?.let { arguments[it] = builder.irGet(thisParam) }
            }
        }
        val backing = property.backingField
        if (backing != null && backing.type.isSubtypeOfClass(typeSymbol)) {
            return builder.irGetField(builder.irGet(thisParam), backing)
        }
        return null
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
            val thisReceiver = clazz.thisReceiver
                ?: error("log4k-compiler-plugin: class `${clazz.name}` has no `this` receiver to synthesize a field for.")
            initializer = pluginContext.irFactory.createExpressionBody(
                clazz.startOffset,
                clazz.endOffset,
                initBuilder.irOfThisClass(pluginContext, ofFunction, thisReceiver),
            )
        }
    }

    /** Attaches every synthesized field to its class. Must run after the module transform. */
    fun commit() {
        // First in the declaration list, so the field is initialized before any user property or
        // `init` block — an instrumented method invoked during construction must already see it.
        // Safe to hoist: the initializer only reads `this::class`, never instance state.
        created.forEach { (clazz, field) -> clazz.declarations.add(0, field) }
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
                val regular = symbol.owner.regularParams()
                regular.size == 1 && regular[0].type.classOrNull == pluginContext.irBuiltIns.kClassClass
            } ?: return null
            return OfThisClassField(
                pluginContext, messageCollector, typeSymbol, ofFunction, annotation, memberName, syntheticName,
            )
        }
    }
}
