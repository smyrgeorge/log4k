package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.impl.extensions.toName

class SimpleConsoleMeteringAppender : Appender<MeteringEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: MeteringEvent) {
        println(event.toString())
    }

    companion object {
        /**
         * Registers a [SimpleConsoleMeteringAppender] with the metering appender registry — by default
         * unregistering every other metering appender in the same atomic step (see
         * [io.github.smyrgeorge.log4k.impl.registry.AppenderRegistry.install]).
         *
         * @param unregisterOthers Whether to unregister every other metering appender (default `true`).
         * @return The registered appender, e.g. for later unregistration.
         */
        fun install(unregisterOthers: Boolean = true): SimpleConsoleMeteringAppender =
            RootLogger.Metering.appenders.install(SimpleConsoleMeteringAppender(), unregisterOthers)
    }
}
