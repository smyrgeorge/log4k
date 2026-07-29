package io.github.smyrgeorge.log4k.integrations.util

import kotlin.reflect.KClass

internal actual fun KClass<*>.toName(): String =
    qualifiedName ?: simpleName ?: error("Could not extract the class-name of $this")
