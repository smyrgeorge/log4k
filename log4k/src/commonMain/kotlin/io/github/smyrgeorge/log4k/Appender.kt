package io.github.smyrgeorge.log4k

interface Appender<T> {
    val name: String
    suspend fun append(event: T)
}