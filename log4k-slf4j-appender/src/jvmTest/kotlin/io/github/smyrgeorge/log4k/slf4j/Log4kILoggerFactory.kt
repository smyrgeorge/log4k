package io.github.smyrgeorge.log4k.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger

/**
 * A stand-in reproducing the exact fully qualified name of the `log4k-slf4j` provider's logger factory,
 * which is what the loop guard keys on. The real class lives in a module this one must not depend on —
 * having both on the classpath is precisely the configuration the guard exists to reject.
 */
class Log4kILoggerFactory : ILoggerFactory {
    override fun getLogger(name: String): Logger = NOPLogger.NOP_LOGGER
}
