package io.github.smyrgeorge.log4k.annotation

/**
 * A single static key/value tag attached to a [Traced] span, e.g.
 * `@Traced(tags = [Tag("component", "billing")])`.
 *
 * Kotlin annotation parameters cannot be a `Map`, so tags are expressed as an array of this
 * annotation; the `log4k-compiler-plugin` materializes them onto the span's `tags` at compile
 * time.
 *
 * @property key The tag key.
 * @property value The tag value.
 */
@Retention(AnnotationRetention.BINARY)
annotation class Tag(val key: String, val value: String)
