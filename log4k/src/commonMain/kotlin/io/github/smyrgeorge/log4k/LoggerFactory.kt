package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

@Suppress("unused")
interface LoggerFactory {
    fun getLogger(clazz: KClass<*>): Logger = getLogger(clazz.toName())
    fun getLogger(name: String): Logger
}