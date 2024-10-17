package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.RootLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test

class MainTests {

    private val log: Logger = Logger.of(this::class)

    @Test
    fun test() {
        log.debug("ignore")
        log.info("this is a test")
        RootLogger.loggers.mute("io.github.smyrgeorge.log4k.MainTests")
        log.info("this is a test with 1 arg: {}", "hello")
        RootLogger.loggers.unmute(this::class)
        log.info("this is a test with 1 arg: {}", "hello")

        try {
            error("An error occurred!")
        } catch (e: Exception) {
            log.error(e.message)
            log.error(e.message, e)
        }

        runBlocking {
            withContext(Dispatchers.IO) {
                delay(2000)
            }
        }
    }
}