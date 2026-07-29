package io.github.smyrgeorge.log4k.compiler.ir.utils

import io.github.smyrgeorge.log4k.compiler.ir.Log4kIrFunctionExpression
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetClassImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
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
 * [buildInlineLambda] wrapped in the [Log4kIrFunctionExpression] the transformers pass as the `f`
 * argument of the inline runtime helpers (`logged`/`measure`/`traced`): a `() -> T` — or, with
 * [extensionReceiverType], an `R.() -> T` — carrying [enclosing]'s original body. The moved body is
 * reachable through [Log4kIrFunctionExpression.function] (e.g., to prepend statements to it).
 */
fun IrPluginContext.buildInlineLambdaExpression(
    enclosing: IrFunction,
    returnType: IrType,
    extensionReceiverType: IrType? = null,
    extensionReceiverName: Name = Name.identifier($$"$this$lambda"),
): Log4kIrFunctionExpression {
    val lambda = buildInlineLambda(enclosing, returnType, extensionReceiverType, extensionReceiverName)
    val type =
        if (extensionReceiverType == null) irBuiltIns.functionN(0).symbol.typeWith(returnType)
        else irBuiltIns.functionN(1).symbol.typeWith(extensionReceiverType, returnType)
    return Log4kIrFunctionExpression(
        startOffset = enclosing.startOffset,
        endOffset = enclosing.endOffset,
        type = type,
        origin = IrStatementOrigin.LAMBDA,
        function = lambda,
    )
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
        ofFn.owner.dispatchReceiverParam()?.let {
            val companion = it.type.classOrNull
                ?: error("log4k-compiler-plugin: the `of` factory's dispatch receiver is not a class type.")
            arguments[it] = irGetObjectValue(it.type, companion)
        }
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

/**
 * The instrumentation name configured on [this] function's [annotation]: its first argument
 * (`@Timed(name = …)` / `@Traced(name = …)`) when it is a non-blank string constant, else the
 * default [qualifiedName].
 */
fun IrFunction.instrumentationName(annotation: FqName): String {
    val configured = (getAnnotation(annotation)?.arguments?.getOrNull(0) as? IrConst)?.value as? String
    return if (configured.isNullOrBlank()) qualifiedName() else configured
}

/** The single dispatch-receiver parameter of [this], or `null` (new-API `parameters` accessor). */
fun IrFunction.dispatchReceiverParam(): IrValueParameter? =
    parameters.singleOrNull { it.kind == IrParameterKind.DispatchReceiver }

/** The regular (value) parameters of [this] — excluding receivers and context parameters. */
fun IrFunction.regularParams(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

/**
 * The first context parameter or extension receiver of [this] function whose type is a subtype of
 * [type] — i.e., a value of [type] "in scope" for the function. Used to pick up a `TracingContext` or
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
fun IrFunction.isClassLevelEligible(): Boolean {
    if (this !is IrSimpleFunction) return false // exclude constructors
    if (visibility != DescriptorVisibilities.PUBLIC) return false
    if (isFakeOverride) return false // exclude inherited members
    if (correspondingPropertySymbol != null) return false // exclude property accessors
    return true
}

/**
 * Whether [this] function is a target of the instrumentation driven by [annotation]: it must have a
 * body, not be disabled by [killSwitch] (on the function or its class — the per-class opt-out wins
 * over any [annotation]), and either carry [annotation] itself or be an eligible member (see
 * [isClassLevelEligible]) of a class annotated with it.
 *
 * Shared by the `@Logged`/`@Timed`/`@Traced` transformers, paired with their respective
 * `@NoLog`/`@NoTime`/`@NoTrace` kill switches.
 */
fun IrFunction.isInstrumentationTarget(annotation: FqName, killSwitch: FqName): Boolean {
    if (body == null) return false
    val enclosingClass = parentClassOrNull
    if (hasAnnotation(killSwitch)) return false
    if (enclosingClass?.hasAnnotation(killSwitch) == true) return false
    // Explicit annotation on the function.
    if (hasAnnotation(annotation)) return true
    // Class-level annotation: instrument every eligible public member function.
    if (enclosingClass == null || !enclosingClass.hasAnnotation(annotation)) return false
    return isClassLevelEligible()
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

/** The `SourceLocation(file, line, function)` constructor, or `null` on an older log4k runtime. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun DeclarationFinder.findSourceLocationConstructor(): IrConstructorSymbol? =
    findConstructors(ClassId(LOG4K_PACKAGE, Name.identifier("SourceLocation")))
        .firstOrNull { it.owner.regularParams().size == 3 }

/**
 * The log4k member function [name] declared on [className] (dot-separated for nested classes, e.g.
 * `"Meter.Timed"`) with exactly [regularParams] regular parameters, or `null` when the runtime on
 * the classpath does not provide that overload. The parameter count pins the exact overload the
 * plugin was built against, so an unexpected runtime degrades to "not found" instead of a
 * mis-shaped call.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun DeclarationFinder.findLog4kFunction(className: String, name: String, regularParams: Int): IrSimpleFunctionSymbol? =
    findFunctions(CallableId(ClassId(LOG4K_PACKAGE, FqName(className), false), Name.identifier(name)))
        .firstOrNull { it.owner.regularParams().size == regularParams }

/**
 * Builds `SourceLocation(file, line, function)` for the source [offset] inside [file] — the value the
 * instrumentation passes attach to emitted events (a call site in
 * [io.github.smyrgeorge.log4k.compiler.callsite.CallSiteIrTransformer], a function declaration in
 * [io.github.smyrgeorge.log4k.compiler.logged.LoggedIrTransformer]). Returns `null` for synthetic
 * [offset]s — there is no source position to record.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrBuilderWithScope.irSourceLocation(
    constructor: IrConstructorSymbol,
    file: IrFile,
    offset: Int,
    function: String,
): IrExpression? {
    if (offset < 0) return null
    val params = constructor.owner.regularParams()
    if (params.size != 3) return null
    val entry = file.fileEntry
    // The simple file name only — a full path would leak build-machine directory layouts.
    val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\')
    return irCallConstructor(constructor, emptyList()).apply {
        arguments[params[0]] = irString(fileName)
        arguments[params[1]] = irInt(entry.getLineNumber(offset) + 1) // IrFileEntry lines are 0-based.
        arguments[params[2]] = irString(function)
    }
}
