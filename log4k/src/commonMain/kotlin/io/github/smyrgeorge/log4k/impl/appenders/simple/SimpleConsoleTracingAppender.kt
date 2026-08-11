package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.TracingEvent
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleTracingAppender : Appender<TracingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: TracingEvent) {
        println(event.toString())
    }

    companion object {
        /**
         * Registers a [SimpleConsoleTracingAppender] with the tracing appender registry — by default
         * unregistering every other tracing appender in the same atomic step (see
         * [io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry.install]).
         *
         * @param unregisterOthers Whether to unregister every other tracing appender (default `true`).
         * @return The registered appender, e.g. for later unregistration.
         */
        fun install(unregisterOthers: Boolean = true): SimpleConsoleTracingAppender =
            RootLogger.Tracing.appenders.install(SimpleConsoleTracingAppender(), unregisterOthers)
    }
}
