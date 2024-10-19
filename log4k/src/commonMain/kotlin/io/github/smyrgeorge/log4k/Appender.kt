package io.github.smyrgeorge.log4k

interface Appender<T> {
    val name: String
    fun append(event: T)
}