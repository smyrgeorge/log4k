package io.github.smyrgeorge.log4k.integrations.util

import io.github.smyrgeorge.log4k.TracingEvent
import kotlin.time.Instant

/**
 * The spans of the batch that can actually be reported: started and ended. Anything else
 * (e.g., a remote context carrier) has no timing information.
 */
internal fun List<TracingEvent>.finishedSpans(): List<TracingEvent.Span> =
    filterIsInstance<TracingEvent.Span>().filter { it.startAt != null && it.endAt != null }

internal fun Instant.epochNanos(): Long = epochSeconds * 1_000_000_000L + nanosecondsOfSecond

internal fun String.isHex(): Boolean = all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/** Both Datadog and OpenTelemetry reserve the all-zero id as the invalid/null sentinel. */
internal fun ULong.nonZero(): ULong = if (this == 0uL) 1uL else this

/** Stable 64-bit hash for ids that are not valid hexadecimal and cannot be parsed directly. */
internal fun fnv1a64(value: String): ULong {
    var hash = 0xcbf29ce484222325uL
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toUByte().toULong()
        hash *= 0x100000001b3uL
    }
    return hash.nonZero()
}
