package io.github.smyrgeorge.log4k.integrations.util

import kotlin.reflect.KClass

// `qualifiedName` is unsupported on wasm.
internal actual fun KClass<*>.toName(): String =
    simpleName ?: error("Could not extract the class-name of $this")
