package io.github.smyrgeorge.log4k

/**
 * Defines a generic interface for appenders that can process and append events of a specified type.
 *
 * @param T The type of event to be appended.
 */
interface Appender<T> {
    val name: String
    suspend fun append(event: T)
}