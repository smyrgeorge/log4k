package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent

actual fun platformDefaultAppender(): Appender<LoggingEvent> = AndroidLoggingAppender()
