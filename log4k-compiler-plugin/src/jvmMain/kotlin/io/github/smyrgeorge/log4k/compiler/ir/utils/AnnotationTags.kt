package io.github.smyrgeorge.log4k.compiler.ir.utils

import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Reads the `tags = [Tag(key, value), …]` array of [annotation] — parameter index 1 on `@Logged`,
 * `@Timed` and `@Traced` alike — into (key, value) pairs.
 *
 * Class-level tags come first and the function's own tags after, so downstream a function tag with
 * the same key wins (later `Map` entries / `put`s overwrite earlier ones).
 *
 * Shared by the `@Logged`, `@Timed` and `@Traced` transformers.
 */
fun IrFunction.resolveTags(annotation: FqName): List<Pair<String, String>> {
    val classTags = tagsOf(parentClassOrNull?.getAnnotation(annotation))
    val functionTags = tagsOf(getAnnotation(annotation))
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

/**
 * Materializes annotation tags as IR expressions. Resolves the required stdlib symbols
 * (`kotlin.Pair`, `kotlin.collections.mapOf`/`emptyMap`) once and offers the two shapes the
 * transformers need: a `Map<String, Any>` argument ([buildMap], used by `@Logged` for
 * `Logger.logged`) and a `vararg tags: Pair<String, Any>` argument ([buildVararg], used by
 * `@Timed` for `Meter.timed`).
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class AnnotationTagsBuilder(
    private val pluginContext: IrPluginContext,
    finder: DeclarationFinder,
    private val messageCollector: MessageCollector,
) {
    private val pairClassSymbol: IrClassSymbol? =
        finder.findClass(ClassId(FqName("kotlin"), Name.identifier("Pair")))
    private val pairConstructor: IrConstructorSymbol? = pairClassSymbol?.owner?.primaryConstructor?.symbol
    private val mapOfFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(FqName("kotlin.collections"), Name.identifier("mapOf")),
    ).firstOrNull { symbol ->
        symbol.owner.regularParams().singleOrNull()?.varargElementType != null
    }
    private val emptyMapFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(FqName("kotlin.collections"), Name.identifier("emptyMap")),
    ).firstOrNull()

    /**
     * Builds the [tags] as a `Map<String, Any>` expression typed as [mapType]: `emptyMap()` when
     * there are none, else `mapOf(Pair(k, v), …)` (later entries win, giving function tags priority
     * over class-level ones). Returns `null` — after reporting against [annotationName] — when the
     * required stdlib symbols cannot be resolved.
     */
    fun buildMap(
        builder: DeclarationIrBuilder,
        function: IrFunction,
        tags: List<Pair<String, String>>,
        mapType: IrType,
        annotationName: String,
    ): IrExpression? {
        val stringType = pluginContext.irBuiltIns.stringType
        val anyType = pluginContext.irBuiltIns.anyType

        if (tags.isEmpty()) {
            val emptyMapFn = emptyMapFunction ?: return unresolved(function, annotationName)
            return builder.irCall(emptyMapFn, mapType, listOf(stringType, anyType))
        }

        val mapOfFn = mapOfFunction ?: return unresolved(function, annotationName)
        val pairClass = pairClassSymbol ?: return unresolved(function, annotationName)
        val varargParam = mapOfFn.owner.regularParams().single()
        val pairType = pairClass.typeWith(stringType, anyType)
        val varargType = pluginContext.irBuiltIns.arrayClass.typeWith(pairType)
        val vararg = pairVararg(function, varargType, pairType, tags, builder)
            ?: return unresolved(function, annotationName)
        return builder.irCall(mapOfFn, mapType, listOf(stringType, anyType)).apply {
            arguments[varargParam] = vararg
        }
    }

    /**
     * Builds the [tags] as the `vararg tags: Pair<String, Any>` argument described by [tagsParam]
     * (e.g. `Meter.timed`'s). When `Pair` cannot be resolved, the failure is reported against
     * [annotationName] and the empty vararg is returned, so instrumentation proceeds without tags.
     */
    fun buildVararg(
        builder: DeclarationIrBuilder,
        function: IrFunction,
        tags: List<Pair<String, String>>,
        tagsParam: IrValueParameter,
        annotationName: String,
    ): IrExpression {
        val elementType = tagsParam.varargElementType ?: tagsParam.type
        if (tags.isEmpty()) return IrVarargImpl(function.startOffset, function.endOffset, tagsParam.type, elementType)
        return pairVararg(function, tagsParam.type, elementType, tags, builder)
            ?: IrVarargImpl(function.startOffset, function.endOffset, tagsParam.type, elementType)
                .also { unresolved(function, annotationName) }
    }

    /** A vararg of `Pair(key, value)` constructor calls, or `null` when `Pair` is unresolved. */
    private fun pairVararg(
        function: IrFunction,
        varargType: IrType,
        elementType: IrType,
        tags: List<Pair<String, String>>,
        builder: DeclarationIrBuilder,
    ): IrVararg? {
        val constructor = pairConstructor ?: return null
        val stringType = pluginContext.irBuiltIns.stringType
        val anyType = pluginContext.irBuiltIns.anyType
        val vararg = IrVarargImpl(function.startOffset, function.endOffset, varargType, elementType)
        tags.forEach { (key, value) ->
            vararg.elements.add(
                builder.irCallConstructor(constructor, listOf(stringType, anyType)).apply {
                    arguments[0] = builder.irString(key)
                    arguments[1] = builder.irString(value)
                },
            )
        }
        return vararg
    }

    private fun unresolved(function: IrFunction, annotationName: String): IrExpression? {
        messageCollector.reportError(
            function,
            "log4k-compiler-plugin: could not apply $annotationName tags (unresolved `mapOf`/`emptyMap`/`Pair`).",
        )
        return null
    }
}
