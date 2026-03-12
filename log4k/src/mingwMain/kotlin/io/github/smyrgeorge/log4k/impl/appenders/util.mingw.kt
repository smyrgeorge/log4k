package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleConsoleLoggingAppender

actual fun platformDefaultAppender(): Appender<LoggingEvent> = SimpleConsoleLoggingAppender()