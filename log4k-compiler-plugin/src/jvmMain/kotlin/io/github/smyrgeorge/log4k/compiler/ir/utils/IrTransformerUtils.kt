package io.github.smyrgeorge.log4k.compiler.ir.utils

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrGetClassImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** The root package of the log4k runtime API. */
val LOG4K_PACKAGE = FqName("io.github.smyrgeorge.log4k")

/**
 * Detaches the original body from [function] and re-homes it inside [lambda], so it can be used as
 * the body of an inline lambda that wraps the original code (e.g. `span { … }` or `logged { … }`).
 *
 * All non-local `return`s that targeted [function] are retargeted to [lambda] so that the wrapping
 * inline helper observes a normal return value instead of a return out of the enclosing function.
 *
 * Shared by [io.github.smyrgeorge.log4k.compiler.trace.TraceIrTransformer] and
 * [io.github.smyrgeorge.log4k.compiler.logged.LoggedIrTransformer].
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrPluginContext.moveBody(function: IrFunction, lambda: IrFunction): IrBlockBody {
    val block = when (val original = function.body) {
        is IrBlockBody -> original
        is IrExpressionBody ->
            DeclarationIrBuilder(this, lambda.symbol).irBlockBody {
                +irReturn(original.expression)
            }

        else -> DeclarationIrBuilder(this, lambda.symbol).irBlockBody { }
    }
    block.transform(
        object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                if (expression.returnTargetSymbol == function.symbol) {
                    expression.returnTargetSymbol = lambda.symbol
                }
                return super.visitReturn(expression)
            }
        },
        null,
    )
    block.patchDeclarationParents(lambda)
    return block
}

/**
 * Builds the inline lambda that wraps [enclosing]'s original body (moved in via [moveBody]) so it can
 * be passed to an `inline` helper such as `span { … }`, `logged { … }` or `measure { … }`.
 *
 * When [extensionReceiverType] is provided, the lambda gains an extension receiver of that type
 * (e.g. `Span.Local.() -> T` for `@Traced`); otherwise it is a plain `() -> T`.
 */
fun IrPluginContext.buildInlineLambda(
    enclosing: IrFunction,
    returnType: IrType,
    extensionReceiverType: IrType? = null,
    extensionReceiverName: Name = Name.identifier($$"$this$lambda"),
): IrSimpleFunction = irFactory.buildFun {
    name = Name.special("<anonymous>")
    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    visibility = DescriptorVisibilities.LOCAL
    modality = Modality.FINAL
    this.returnType = returnType
    isSuspend = false
}.apply {
    parent = enclosing
    if (extensionReceiverType != null) {
        val receiver = buildValueParameter(this) {
            name = extensionReceiverName
            kind = IrParameterKind.ExtensionReceiver
            type = extensionReceiverType
        }
        parameters = listOf(receiver)
    }
    body = moveBody(enclosing, this)
}

/**
 * Builds `<Companion>.of(this::class)` for a companion `of(KClass<*>)` factory such as
 * `Logger.of` or `Meter.of`, using [thisReceiver] (a class or function dispatch receiver) as `this`.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun DeclarationIrBuilder.irOfThisClass(
    pluginContext: IrPluginContext,
    ofFn: IrSimpleFunctionSymbol,
    thisReceiver: IrValueParameter,
): IrExpression {
    val kClassType = pluginContext.irBuiltIns.kClassClass.typeWith(thisReceiver.type)
    val getClass = IrGetClassImpl(thisReceiver.startOffset, thisReceiver.endOffset, kClassType, irGet(thisReceiver))
    return irCall(ofFn).apply {
        ofFn.owner.dispatchReceiverParam()?.let { arguments[it] = irGetObjectValue(it.type, it.type.classOrNull!!) }
        ofFn.owner.regularParams().firstOrNull()?.let { arguments[it] = getClass }
    }
}

/**
 * The instrumentation name of [this] function: `"ClassName.functionName"`, or just the function
 * name for a top-level function. Used as the default span name (`@Traced`) and the log name (`@Logged`).
 */
fun IrFunction.qualifiedName(): String {
    val functionName = name.asString()
    val className = parentClassOrNull?.name?.asString()
    return if (className != null) "$className.$functionName" else functionName
}

/** The single dispatch-receiver parameter of [this], or `null` (new-API `parameters` accessor). */
fun IrFunction.dispatchReceiverParam(): IrValueParameter? =
    parameters.singleOrNull { it.kind == IrParameterKind.DispatchReceiver }

/** The regular (value) parameters of [this] — excluding receivers and context parameters. */
fun IrFunction.regularParams(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

/**
 * The first context parameter or extension receiver of [this] function whose type is a subtype of
 * [type] — i.e. a value of [type] "in scope" for the function. Used to pick up a `TracingContext` or
 * `TracingEvent.Span` provided via `context(_: …)` or an extension receiver.
 */
fun IrFunction.receiverOrContextOf(type: IrClassSymbol): IrValueParameter? =
    parameters.firstOrNull {
        (it.kind == IrParameterKind.Context || it.kind == IrParameterKind.ExtensionReceiver) &&
                it.type.isSubtypeOfClass(type)
    }

/**
 * Whether [this] function is an eligible target for **class-level** instrumentation: a public,
 * concrete member function that is not a constructor, property accessor, or inherited (fake-override)
 * member.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrFunction.isClassLevelEligible(): Boolean {
    if (this !is IrSimpleFunction) return false // exclude constructors
    if (visibility != DescriptorVisibilities.PUBLIC) return false
    if (isFakeOverride) return false // exclude inherited members
    if (correspondingPropertySymbol != null) return false // exclude property accessors
    return true
}

/**
 * Reports a compilation error anchored at [function], failing the build. Returns `null` so it can be
 * used as `?: return messageCollector.reportError(...)` from functions with a nullable return type.
 */
fun MessageCollector.reportError(function: IrFunction, message: String): Nothing? {
    report(CompilerMessageSeverity.ERROR, message, function.compilerLocation())
    return null
}

private fun IrFunction.compilerLocation(): CompilerMessageLocation? {
    val entry = fileOrNull?.fileEntry ?: return null
    // IrFileEntry line/column are 0-based; compiler messages are 1-based.
    return CompilerMessageLocation.create(
        entry.name,
        entry.getLineNumber(startOffset) + 1,
        entry.getColumnNumber(startOffset) + 1,
        null,
    )
}
