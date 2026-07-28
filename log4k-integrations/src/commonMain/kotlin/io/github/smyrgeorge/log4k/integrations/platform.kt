package io.github.smyrgeorge.log4k.integrations

import kotlin.reflect.KClass

/**
 * The best available name for a class on the current platform: the fully-qualified name where
 * the platform supports it (JVM, native), the simple name otherwise (JS, wasm). Mirrors the
 * `toName()` extension of the main log4k module, which is internal there.
 */
internal expect fun KClass<*>.toName(): String
