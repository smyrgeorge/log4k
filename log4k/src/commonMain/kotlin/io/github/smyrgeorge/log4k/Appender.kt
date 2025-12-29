package io.github.smyrgeorge.log4k

/**
 * An interface for appending events of type T.
 *
 * This interface defines a contract for any appender that is responsible
 * for handling or appending events. Each appender implementation should
 * specify how to append events and specify a unique name.
 *
 * @param T The type of event that this appender should handle.
 */
interface Appender<T> {
    val name: String
    suspend fun append(event: T)
}
