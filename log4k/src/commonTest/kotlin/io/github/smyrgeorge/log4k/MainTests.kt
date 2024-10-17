package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.appenders.BatchAppender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class MainTests {

    class MyBatchAppender(size: Int) : BatchAppender(size) {
        override suspend fun append(event: List<LoggingEvent>) {
            // E.g. send batch over http.
            println(event.joinToString { it.message })
        }
    }

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
            delay(2000)
            val appender = MyBatchAppender(5)
            RootLogger.appenders.register(appender)

            repeat(10) {
                log.info("$it")
                delay(500)
            }

            delay(2000)
        }
    }
}