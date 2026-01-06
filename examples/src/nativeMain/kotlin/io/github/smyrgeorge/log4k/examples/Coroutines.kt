package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.Tracer
import io.github.smyrgeorge.log4k.TracingContext
import io.github.smyrgeorge.log4k.TracingContext.Companion.span
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.coroutines.Logger
import io.github.smyrgeorge.log4k.coroutines.impl.SimpleCoroutinesLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class Coroutines {
    fun run() = runBlocking {
        Logger.factory = SimpleCoroutinesLoggerFactory()
        val trace: Tracer = Tracer.of(this::class)
        val log = Logger.of(this::class)

        log.info("Hello from coroutines logger!")

        val parent: TracingEvent.Span.Remote = trace.span(id = "ID_EXAMPLE", traceId = "TRACE_ID_EXAMPLE")
        val ctx = TracingContext.builder().with(parent).build()

        withContext(ctx) {
            val ctx = TracingContext.current()
            log.info("1. Hello from coroutines logger with context=$ctx!")

            ctx.span("span-1") {
                log.info("2. Hello from span '${ctx.currentOrNull()?.name}'!")
                ctx.span("span-2") {
                    log.info("3. Hello from span '${ctx.currentOrNull()?.name}'!")
                    ctx.span("span-3") {
                        log.info("4. Hello from span '${ctx.currentOrNull()?.name}'!")
                    }
                }
            }

            log.info("5. Hello from coroutines logger with context=$ctx!")
        }

        delay(5000)
        log.info("Finished coroutines test.")
    }
}