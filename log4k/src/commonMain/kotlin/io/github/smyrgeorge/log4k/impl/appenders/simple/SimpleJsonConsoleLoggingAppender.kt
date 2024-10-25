package io.github.smyrgeorge.log4k.impl.appenders.simple

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.LoggingEvent
import io.github.smyrgeorge.log4k.impl.extensions.format
import io.github.smyrgeorge.log4k.impl.extensions.platformPrintlnError
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Suppress("MemberVisibilityCanBePrivate")
class SimpleJsonConsoleLoggingAppender : Appender<LoggingEvent> {
    override val name: String = this::class.toName()
    override suspend fun append(event: LoggingEvent) = event.printJson()

    companion object {
        fun LoggingEvent.printJson() {
            val message = formatJson()
            if (level == Level.ERROR) platformPrintlnError(message)
            else println(message)
        }

        private fun LoggingEvent.formatJson(): String {
            val map = buildMap<String, Any?> {
                if (id > 0) put("id", id)
                put("level", level.name)
                put("span_id", span?.context?.spanId)
                put("trace_id", span?.context?.traceId)
                put("timestamp", timestamp)
                put("logger", logger)
                put("message", message.format(arguments))
                put("thread", thread)
                put("throwable", throwable?.stackTraceToString())
            }
            return map.toJsonElement().toString()
        }

        fun primitive(value: Any): JsonPrimitive = when (value) {
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }

        fun List<*>.toJsonElement(): JsonElement {
            val list: MutableList<JsonElement> = mutableListOf()
            forEach {
                val value = it ?: return@forEach
                when (value) {
                    is Map<*, *> -> list.add((value).toJsonElement())
                    is List<*> -> list.add(value.toJsonElement())
                    else -> list.add(primitive(value))
                }
            }
            return JsonArray(list)
        }

        fun Map<*, *>.toJsonElement(): JsonElement {
            val map: MutableMap<String, JsonElement> = mutableMapOf()
            forEach {
                val key = it.key as? String ?: return@forEach
                val value = it.value ?: return@forEach
                when (value) {
                    is Map<*, *> -> map[key] = (value).toJsonElement()
                    is List<*> -> map[key] = value.toJsonElement()
                    else -> map[key] = primitive(value)
                }
            }
            return JsonObject(map)
        }
    }
}