package io.github.smyrgeorge.log4k.compiler.callsite

import io.github.smyrgeorge.log4k.compiler.ir.utils.LOG4K_PACKAGE
import io.github.smyrgeorge.log4k.compiler.ir.utils.LevelSymbols
import io.github.smyrgeorge.log4k.compiler.ir.utils.TracingSymbols
import io.github.smyrgeorge.log4k.compiler.ir.utils.dispatchReceiverParam
import io.github.smyrgeorge.log4k.compiler.ir.utils.findLog4kFunction
import io.github.smyrgeorge.log4k.compiler.ir.utils.findSourceLocationConstructor
import io.github.smyrgeorge.log4k.compiler.ir.utils.irSourceLocation
import io.github.smyrgeorge.log4k.compiler.ir.utils.qualifiedName
import io.github.smyrgeorge.log4k.compiler.ir.utils.regularParams
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.DeclarationFinder
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites every call to a log4k logging entry point so the emitted event carries a compile-time
 * `io.github.smyrgeorge.log4k.SourceLocation` — the call's file, line, and enclosing function. Because the
 * values are baked in as constants, accurate source locations cost no runtime stack-walking, and
 * work uniformly on every Kotlin target (including Native, JS, and Wasm, where walking the stack is
 * expensive or impossible).
 *
 * Intercepted entry points, and how each is rewritten:
 * - `Logger.log(level, span, tags, message, arguments, throwable)` — retargeted to the overload with
 *   the trailing `callSite` parameter, all other arguments copied verbatim.
 * - `Logger.at(level) { … }` and the `atTrace`/…/`atError` shorthands — retargeted to
 *   `at(level, callSite, f)`; the builder lambda stays in inline position, so its laziness (and any
 *   non-local returns inside it) are preserved.
 * - The level-named `trace/debug/info/warn/error(…)` forwarders — the classic extensions
 *   (`log4k-classic`), the context-aware overloads (`log4k-context`, `context(TracingContext)` /
 *   `context(Span)`), and the `log.classic.…` escape hatch (`Log4kClassic`) — remapped directly
 *   onto `Logger.log(…, callSite)`. They are all trivial forwarders, so the plugin re-derives the
 *   mapping from the callee's signature instead of hardcoding each shape: the level comes from the
 *   function name, every value parameter is classified by its type (`Span` / `Tags` / `String`
 *   message / `() -> String` lazy message / `Throwable` / vararg arguments), a `Log4kClassic`
 *   receiver is unwrapped through its `logger` property, and a context parameter contributes the
 *   span (a `Span` directly; a `TracingContext` via `currentOrNull()`). Argument expressions are
 *   evaluated into temporaries in their original order, so side-effect ordering is unchanged. A
 *   lazy `{ … }` message lambda is invoked behind an `isEnabled(level)` guard — mirroring the level
 *   gate the original inline extension compiled to — so a disabled logger still costs neither the
 *   message building nor the lambda allocation.
 *
 * A call is left untouched (keeping a `null` call site, never breaking compilation) when:
 * - A classic lazy-message lambda contains a non-local jump — a `return`, `break`, or `continue`
 *   targeting an enclosing function or loop (it cannot be moved behind the guard);
 * - The callee's shape is not recognized (e.g., a future log4k adds a parameter this plugin predates);
 * - The call has no source offsets (synthetic code).
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class CallSiteIrTransformer(
    private val pluginContext: IrPluginContext,
    private val finder: DeclarationFinder,
) : IrElementTransformerVoidWithContext() {

    // `SourceLocation(file, line, function)` — the constructor every rewrite injects a call to.
    private val callSiteConstructor: IrConstructorSymbol? = finder.findSourceLocationConstructor()

    // `Logger` — used to recognize the classic extensions' receiver.
    private val loggerClassSymbol = finder.findClass(ClassId(LOG4K_PACKAGE, Name.identifier("Logger")))

    // `Logger.log(level, span, tags, message, arguments, throwable)` and its call-site-aware sibling.
    private val logFunction: IrSimpleFunctionSymbol? = finder.findLog4kFunction("Logger", "log", regularParams = 6)
    private val logWithCallSiteFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Logger", "log", regularParams = 7)

    // `Logger.at(level, f)` and its call-site-aware sibling `at(level, callSite, f)`.
    private val atFunction: IrSimpleFunctionSymbol? = finder.findLog4kFunction("Logger", "at", regularParams = 2)
    private val atWithCallSiteFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Logger", "at", regularParams = 3)

    // `Logger.isEnabled(level)` — guards the invocation of a remapped lazy-message lambda.
    private val isEnabledFunction: IrSimpleFunctionSymbol? =
        finder.findLog4kFunction("Logger", "isEnabled", regularParams = 1)

    // The `atTrace`/…/`atError` shorthands (top-level extensions), mapped to their level name.
    private val atShorthands: Map<IrSimpleFunctionSymbol, String> = buildMap {
        LEVEL_NAMES.forEach { level ->
            val name = Name.identifier("at" + level.lowercase().replaceFirstChar(Char::uppercase))
            finder.findFunctions(CallableId(EXTENSIONS_PACKAGE, name)).forEach { put(it, level) }
        }
    }

    // The `Level` enum, to materialize the level implied by a function's name.
    private val levels: LevelSymbols? = LevelSymbols.of(finder)

    // `TracingEvent.Span` / `TracingContext` + `currentOrNull()` — a forwarder's span sources
    // (a `Span` value or context parameter, or a `TracingContext` context parameter's current span).
    private val tracing = TracingSymbols.of(finder)

    // `Log4kClassic` + its `logger` property — the `log.classic` escape hatch (`log4k-context`),
    // a value-class wrapper unwrapped back to the `Logger` it carries. Absent when the module is
    // not on the classpath — its calls are then simply left untouched.
    private val log4kClassicClassSymbol = finder.findClass(ClassId(CONTEXT_PACKAGE, Name.identifier("Log4kClassic")))
    private val log4kClassicLoggerGetter: IrSimpleFunctionSymbol? = finder.findProperties(
        CallableId(ClassId(CONTEXT_PACKAGE, Name.identifier("Log4kClassic")), Name.identifier("logger")),
    ).firstOrNull()?.owner?.getter?.symbol

    // `kotlin.arrayOf` / `kotlin.collections.emptyMap` — fill `Logger.log`'s `arguments` / `tags`.
    private val arrayOfFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(FqName("kotlin"), Name.identifier("arrayOf")),
    ).firstOrNull()
    private val emptyMapFunction: IrSimpleFunctionSymbol? = finder.findFunctions(
        CallableId(FqName("kotlin.collections"), Name.identifier("emptyMap")),
    ).firstOrNull()

    // `Function0.invoke` — invokes a remapped lazy-message lambda behind the level guard.
    private val invokeFunction: IrSimpleFunctionSymbol? = pluginContext.irBuiltIns.functionN(0)
        .functions.singleOrNull { it.name.asString() == "invoke" }?.symbol

    // The log4k call-site API must be on the classpath for the plugin to do anything.
    val isReady: Boolean = callSiteConstructor != null && loggerClassSymbol != null &&
            logFunction != null && logWithCallSiteFunction != null &&
            isEnabledFunction != null && LEVEL_NAMES.all { levels?.entries?.containsKey(it) == true } &&
            tracing.span != null &&
            arrayOfFunction != null && emptyMapFunction != null && invokeFunction != null

    override fun visitCall(expression: IrCall): IrExpression {
        val call = super.visitCall(expression)
        if (call !is IrCall) return call
        // Synthetic code has no source offsets — there is no call site to record.
        if (call.startOffset < 0) return call
        return when {
            call.symbol == logFunction -> rewriteDirectLog(call)
            call.symbol == atFunction -> rewriteAt(call, levelName = null)
            call.symbol in atShorthands -> rewriteAt(call, levelName = atShorthands.getValue(call.symbol))
            else -> rewriteForwarder(call)
        } ?: call
    }

    // --- The injected SourceLocation(file, line, function) ------------------------------------------------

    private fun IrBuilderWithScope.buildCallSite(call: IrCall): IrExpression {
        val constructor = callSiteConstructor
            ?: error("log4k-compiler-plugin: `SourceLocation` constructor not resolved — guarded by `isReady`.")
        return irSourceLocation(constructor, currentFile, call.startOffset, enclosingFunctionName())
            ?: error("log4k-compiler-plugin: no source location for a call with source offsets — guarded by `visitCall`.")
    }

    /**
     * The nearest named function enclosing the call — lambdas (`<anonymous>`) are skipped, so a log
     * call inside `map { … }` is still attributed to the function that contains it. A property
     * accessor is attributed as `ClassName.propertyName`. Falls back to `ClassName.<init>` inside
     * constructors/initializers, and to `<top-level>` for code outside any function (e.g., a
     * top-level property initializer).
     */
    private fun enclosingFunctionName(): String {
        for (scope in allScopes.asReversed()) {
            when (val element = scope.irElement) {
                is IrConstructor -> {
                    val className = (element.parent as? IrClass)?.name?.asString()
                    return if (className != null) "$className.<init>" else "<init>"
                }

                is IrSimpleFunction -> {
                    if (!element.name.isSpecial) return element.qualifiedName()
                    // A getter/setter has a special name (`<get-x>`) but is a named attribution
                    // point: use its property's name, never the `<init>` fallback.
                    val property = element.correspondingPropertySymbol?.owner
                    if (property != null) {
                        val className = (element.parent as? IrClass)?.name?.asString()
                        val propertyName = property.name.asString()
                        return if (className != null) "$className.$propertyName" else propertyName
                    }
                }

                else -> {}
            }
        }
        val className = (currentClass?.irElement as? IrClass)?.name?.asString()
        return className?.let { "$it.<init>" } ?: "<top-level>"
    }

    private fun builderAt(call: IrCall): DeclarationIrBuilder {
        val scope = currentScope
            ?: error("log4k-compiler-plugin: no enclosing scope for a rewritten call — calls always sit inside a declaration.")
        return DeclarationIrBuilder(pluginContext, scope.scope.scopeOwnerSymbol, call.startOffset, call.endOffset)
    }

    private fun levelEnum(call: IrCall, name: String): IrExpression =
        levels?.get(name, call.startOffset, call.endOffset)
            ?: error("log4k-compiler-plugin: `Level.$name` not resolved — guarded by `isReady`.")

    // --- Logger.log(…) -> Logger.log(…, callSite) ---------------------------------------------------

    private fun rewriteDirectLog(call: IrCall): IrExpression? {
        val target = logWithCallSiteFunction ?: return null
        val targetDispatch = target.owner.dispatchReceiverParam() ?: return null
        val targetRegular = target.owner.regularParams()
        val sourceDispatch = call.symbol.owner.dispatchReceiverParam() ?: return null
        val sourceRegular = call.symbol.owner.regularParams()
        if (targetRegular.size != sourceRegular.size + 1) return null

        val receiverExpr = call.arguments[sourceDispatch] ?: return null
        val builder = builderAt(call)
        val rewritten = builder.irCall(target).apply {
            arguments[targetDispatch] = receiverExpr
        }
        for ((i, param) in sourceRegular.withIndex()) {
            rewritten.arguments[targetRegular[i]] = call.arguments[param] ?: return null
        }
        rewritten.arguments[targetRegular.last()] = builder.buildCallSite(call)
        return rewritten
    }

    // --- at(level, f) / atInfo(f) / … -> at(level, callSite, f) --------------------------------------

    private fun rewriteAt(call: IrCall, levelName: String?): IrExpression? {
        val target = atWithCallSiteFunction ?: return null
        val targetDispatch = target.owner.dispatchReceiverParam() ?: return null
        val targetRegular = target.owner.regularParams()
        if (targetRegular.size != 3) return null

        val source = call.symbol.owner
        // `at` is a member (dispatch receiver); the `atX` shorthands are extensions (extension receiver).
        val sourceReceiver = source.parameters.firstOrNull {
            it.kind == IrParameterKind.DispatchReceiver || it.kind == IrParameterKind.ExtensionReceiver
        } ?: return null
        val receiverExpr = call.arguments[sourceReceiver] ?: return null
        val sourceRegular = source.regularParams()
        val fExpr = call.arguments[sourceRegular.last()] ?: return null

        val builder = builderAt(call)
        val levelExpr = when (levelName) {
            null -> call.arguments[sourceRegular[0]] ?: return null // `at(level, f)` — copy the argument.
            else -> levelEnum(call, levelName) // `atInfo(f)` etc. — the name implies the level.
        }
        return builder.irCall(target).apply {
            arguments[targetDispatch] = receiverExpr
            arguments[targetRegular[0]] = levelExpr
            arguments[targetRegular[1]] = builder.buildCallSite(call)
            arguments[targetRegular[2]] = fExpr
        }
    }

    // --- level-named forwarders (log4k-classic + log4k-context) -> Logger.log(…, callSite) -----------

    /** How a forwarder's value parameter maps onto `Logger.log`'s parameters. */
    private enum class Role { SPAN, TAGS, MESSAGE, LAZY_MESSAGE, THROWABLE, ARGUMENTS }

    private fun rewriteForwarder(call: IrCall): IrExpression? {
        val callee = call.symbol.owner
        if (callee.name !in FORWARDER_NAMES) return null
        val pkg = callee.getPackageFragment().packageFqName
        if (pkg != CLASSIC_PACKAGE && pkg != CONTEXT_PACKAGE) return null
        val levelName = callee.name.asString().uppercase()

        // The receiver carrying the logger: a `Logger` directly, or the `Log4kClassic` value-class
        // wrapper (the `log.classic` escape hatch) — unwrapped through its `logger` property.
        val receiverParam =
            callee.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver } ?: return null
        val receiverClass = receiverParam.type.classOrNull ?: return null
        val unwrapLogger: IrSimpleFunctionSymbol? = when {
            receiverClass == loggerClassSymbol -> null
            log4kClassicClassSymbol != null && receiverClass == log4kClassicClassSymbol ->
                log4kClassicLoggerGetter ?: return null

            else -> return null
        }

        // A span provided through a context parameter (`log4k-context`): a `Span` is attached
        // directly; a `TracingContext` contributes its `currentOrNull()` — mirroring the bodies of
        // the context-aware overloads.
        val spanClass = tracing.span
        val tracingContextClass = tracing.tracingContext
        var spanContextParam: IrValueParameter? = null
        var tracingContextParam: IrValueParameter? = null
        for (param in callee.parameters) {
            if (param.kind != IrParameterKind.Context) continue
            if (spanContextParam != null || tracingContextParam != null) return null // a single source.
            when {
                spanClass != null && param.type.isSubtypeOfClass(spanClass) -> spanContextParam = param
                tracingContextClass != null && param.type.classOrNull == tracingContextClass &&
                        tracing.currentOrNull != null -> tracingContextParam = param

                else -> return null
            }
        }

        // Classify the callee's value parameters by type; bail on anything this plugin does not recognize.
        val roles = LinkedHashMap<IrValueParameter, Role>()
        for (param in callee.regularParams()) {
            val role = classify(param) ?: return null
            if (roles.containsValue(role)) return null // duplicate shapes are not a forwarder signature.
            roles[param] = role
        }
        if ((Role.MESSAGE in roles.values) == (Role.LAZY_MESSAGE in roles.values)) return null // exactly one.
        // The span comes from at most one place: a value parameter or a context parameter.
        if (Role.SPAN in roles.values && (spanContextParam != null || tracingContextParam != null)) return null

        val receiverExpr = call.arguments[receiverParam] ?: return null
        val contextExpr = (spanContextParam ?: tracingContextParam)?.let { call.arguments[it] ?: return null }
        // All forwarder parameters are required except the trailing vararg (absent -> null argument).
        val argExprs = LinkedHashMap<IrValueParameter, IrExpression?>()
        for ((param, role) in roles) {
            val expr = call.arguments[param]
            if (expr == null && role != Role.ARGUMENTS) return null
            if (role == Role.ARGUMENTS && expr != null && expr !is IrVararg) return null
            argExprs[param] = expr
        }
        // A lazy-message lambda with a non-local jump cannot be moved behind the `isEnabled` guard.
        val lazyExpr = argExprs.entries.firstOrNull { roles[it.key] == Role.LAZY_MESSAGE }?.value
        if (lazyExpr is IrFunctionExpression && lazyExpr.hasNonLocalJump()) return null

        val target = logWithCallSiteFunction?.owner
            ?: error("log4k-compiler-plugin: `Logger.log(…, callSite)` not resolved — guarded by `isReady`.")
        val targetDispatch = target.dispatchReceiverParam() ?: return null
        val targetRegular = target.regularParams() // [level, span, tags, message, arguments, throwable, callSite]
        if (targetRegular.size != 7) return null

        val builder = builderAt(call)
        val unitType = pluginContext.irBuiltIns.unitType
        return builder.irBlock(resultType = unitType) {
            // Evaluate the receiver (unwrapped to its Logger), the context value, and every argument
            // into temporaries, in source order.
            val receiver = irTemporary(
                if (unwrapLogger == null) receiverExpr
                else irCall(unwrapLogger).apply {
                    val dispatch = unwrapLogger.owner.dispatchReceiverParam()
                        ?: error("log4k-compiler-plugin: `Log4kClassic.logger` getter has no dispatch receiver.")
                    arguments[dispatch] = receiverExpr
                }
            )
            val contextTemp = contextExpr?.let { irTemporary(it) }
            val temps = HashMap<Role, IrVariable>()
            var lazyLiteral: IrExpression? = null
            for ((param, expr) in argExprs) {
                when (val role = roles.getValue(param)) {
                    Role.ARGUMENTS -> temps[role] = irTemporary(argumentsArray(expr as IrVararg?))
                    Role.LAZY_MESSAGE ->
                        // A lambda literal has no side effects to order — keep it out of the block, so
                        // its allocation happens behind the level guard, like the original inline call.
                        if (expr is IrFunctionExpression) lazyLiteral = expr
                        else temps[role] = irTemporary(expr ?: error(MISSING_ARGUMENT))

                    else -> temps[role] = irTemporary(expr ?: error(MISSING_ARGUMENT))
                }
            }

            val spanArg = when {
                Role.SPAN in roles.values -> irGet(temps.getValue(Role.SPAN))
                spanContextParam != null -> irGet(contextTemp ?: error(MISSING_CONTEXT))
                tracingContextParam != null ->
                    // `ctx.currentOrNull()` — placed inside the log call, so for lazy messages it
                    // stays behind the level guard, exactly like the original overload's body.
                    tracing.irCurrentOrNull(this, irGet(contextTemp ?: error(MISSING_CONTEXT)))
                        ?: error("log4k-compiler-plugin: `TracingContext.currentOrNull` not resolved — validated during classification.")

                else -> irNull(targetRegular[1].type)
            }

            val logCall = irCall(target.symbol).apply {
                arguments[targetDispatch] = irGet(receiver)
                arguments[targetRegular[0]] = levelEnum(call, levelName)
                arguments[targetRegular[1]] = spanArg
                arguments[targetRegular[2]] = temps[Role.TAGS]?.let { irGet(it) } ?: emptyTags()
                arguments[targetRegular[3]] = temps[Role.MESSAGE]?.let { irGet(it) }
                    ?: invokeLazyMessage(lazyLiteral ?: irGet(temps.getValue(Role.LAZY_MESSAGE)))
                arguments[targetRegular[4]] = temps[Role.ARGUMENTS]?.let { irGet(it) } ?: argumentsArray(null)
                arguments[targetRegular[5]] = temps[Role.THROWABLE]?.let { irGet(it) } ?: irNull(targetRegular[5].type)
                arguments[targetRegular[6]] = buildCallSite(call)
            }

            if (Role.MESSAGE in roles.values) {
                // Eager message: `log` performs the level gate itself, exactly like the forwarder did.
                +logCall
            } else {
                // Lazy message: keep the original inline extension's level gate, so the lambda is
                // neither allocated nor invoked when the level is disabled.
                val isEnabled = isEnabledFunction
                    ?: error("log4k-compiler-plugin: `Logger.isEnabled` not resolved — guarded by `isReady`.")
                val enabled = irCall(isEnabled).apply {
                    val dispatch = isEnabled.owner.dispatchReceiverParam()
                        ?: error("log4k-compiler-plugin: `Logger.isEnabled` has no dispatch receiver.")
                    arguments[dispatch] = irGet(receiver)
                    arguments[isEnabled.owner.regularParams()[0]] = levelEnum(call, levelName)
                }
                +irIfThen(unitType, enabled, logCall)
            }
        }
    }

    private fun classify(param: IrValueParameter): Role? {
        val spanClass = tracing.span
        return when {
            param.varargElementType != null -> Role.ARGUMENTS
            param.type.isString() -> Role.MESSAGE
            param.type.classOrNull == pluginContext.irBuiltIns.functionN(0).symbol -> Role.LAZY_MESSAGE
            param.type.classOrNull == pluginContext.irBuiltIns.mapClass -> Role.TAGS
            spanClass != null && param.type.isSubtypeOfClass(spanClass) -> Role.SPAN
            param.type.isSubtypeOfClass(pluginContext.irBuiltIns.throwableClass) -> Role.THROWABLE
            else -> null
        }
    }

    /** Builds `arrayOf<Any?>(…)` from the classic call's vararg (or an empty array when absent). */
    private fun IrBlockBuilder.argumentsArray(vararg: IrVararg?): IrExpression {
        val arrayOf = arrayOfFunction
            ?: error("log4k-compiler-plugin: `kotlin.arrayOf` not resolved — guarded by `isReady`.")
        val anyN = pluginContext.irBuiltIns.anyNType
        val arrayType = pluginContext.irBuiltIns.arrayClass.typeWith(anyN)
        return irCall(arrayOf, arrayType, listOf(anyN)).apply {
            arguments[arrayOf.owner.regularParams()[0]] = vararg ?: irVararg(anyN, emptyList())
        }
    }

    /** Builds `emptyMap<String, Any>()` — the default `tags` of a classic call without them. */
    private fun IrBlockBuilder.emptyTags(): IrExpression {
        val stringType = pluginContext.irBuiltIns.stringType
        val anyType = pluginContext.irBuiltIns.anyType
        val mapType = pluginContext.irBuiltIns.mapClass.typeWith(stringType, anyType)
        val emptyMap = emptyMapFunction
            ?: error("log4k-compiler-plugin: `kotlin.collections.emptyMap` not resolved — guarded by `isReady`.")
        return irCall(emptyMap, mapType, listOf(stringType, anyType))
    }

    /** Builds `<f>.invoke()` — evaluating a lazy `() -> String` message behind the level guard. */
    private fun IrBlockBuilder.invokeLazyMessage(f: IrExpression): IrExpression {
        val invoke = invokeFunction
            ?: error("log4k-compiler-plugin: `Function0.invoke` not resolved — guarded by `isReady`.")
        return irCall(invoke, pluginContext.irBuiltIns.stringType).apply {
            val dispatch = invoke.owner.dispatchReceiverParam()
                ?: error("log4k-compiler-plugin: `Function0.invoke` has no dispatch receiver.")
            arguments[dispatch] = f
        }
    }

    /**
     * Whether the lambda contains a jump that escapes it: a `return` targeting an enclosing
     * function, or a `break`/`continue` targeting an enclosing loop (legal in an inline lambda
     * since Kotlin 2.2). Such a lambda cannot be turned into a regular function object.
     */
    private fun IrFunctionExpression.hasNonLocalJump(): Boolean {
        val localTargets = mutableSetOf<IrSymbol>()
        val localLoops = mutableSetOf<IrLoop>()
        function.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
            override fun visitFunction(declaration: IrFunction) {
                localTargets += declaration.symbol
                declaration.acceptChildrenVoid(this)
            }

            override fun visitLoop(loop: IrLoop) {
                localLoops += loop
                loop.acceptChildrenVoid(this)
            }
        })
        var nonLocal = false
        function.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
            override fun visitReturn(expression: IrReturn) {
                if (expression.returnTargetSymbol !in localTargets) nonLocal = true
                expression.acceptChildrenVoid(this)
            }

            override fun visitBreakContinue(jump: IrBreakContinue) {
                if (jump.loop !in localLoops) nonLocal = true
                jump.acceptChildrenVoid(this)
            }
        })
        return nonLocal
    }

    companion object {
        private val CLASSIC_PACKAGE = FqName("io.github.smyrgeorge.log4k.classic")
        private val CONTEXT_PACKAGE = FqName("io.github.smyrgeorge.log4k.context")
        private val EXTENSIONS_PACKAGE = FqName("io.github.smyrgeorge.log4k.impl.extensions")
        private val LEVEL_NAMES = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")

        // Invariant-violation messages for states the earlier validation makes unreachable.
        private const val MISSING_ARGUMENT =
            "log4k-compiler-plugin: absent argument for a required forwarder parameter — validated before rewriting."
        private const val MISSING_CONTEXT =
            "log4k-compiler-plugin: absent context argument for a span-providing context parameter — validated before rewriting."
        private val FORWARDER_NAMES = LEVEL_NAMES.map { Name.identifier(it.lowercase()) }.toSet()
    }
}
