package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.RootLogger
import io.github.smyrgeorge.log4k.impl.RootLogger.loggers
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

@Suppress("unused")
class LoggerFactory {
    fun getLogger(clazz: KClass<*>): Logger = getLogger(clazz.toName())
    fun getLogger(name: String): Logger {
        val existing = loggers.get(name)
        if (existing != null) return existing
        return SimpleLogger(name, RootLogger.level).also {
            val muted = loggers.isMuted(name)
            if (muted) it.mute()
            loggers.register(it)
        }
    }
}