package io.github.smyrgeorge.log4k.impl.appenders.simple

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Meter.Instrument.Kind
import io.github.smyrgeorge.log4k.MeteringEvent
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.impl.SimpleMeter
import io.github.smyrgeorge.log4k.utils.CapturingMeteringAppender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test

/**
 * Tests for [SimpleMeteringCollectorAppender]. Most tests drive the appender directly (bypassing
 * the `RootLogger` pipeline) and assert the OpenMetrics rendering line-by-line; the last one
 * exercises the full `Meter -> RootLogger -> appender` integration path.
 */
class SimpleMeteringCollectorAppenderTests {

    private var id: Long = 0
    private fun nextId(): Long = ++id

    private suspend fun SimpleMeteringCollectorAppender.create(
        name: String,
        kind: Kind,
        unit: String? = null,
        description: String? = null,
    ) = append(MeteringEvent.CreateInstrument(nextId(), name, kind, unit, description))

    // --- Rendering: overall exposition shape -----------------------------------------------------

    @Test
    fun rendersOpenMetricsCompliantExposition() = runTest {
        // Bucket boundaries are exercised by the dedicated histogram tests below; disabled here
        // to keep this test focused on the overall exposition shape.
        val appender = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = emptyList())

        appender.create("requests", Kind.Counter, description = "Total requests.")
        appender.append(MeteringEvent.Increment(nextId(), "requests", mapOf("path" to "/a"), value = 2L))

        appender.create("pool.size", Kind.UpDownCounter)
        appender.append(MeteringEvent.Increment(nextId(), "pool.size", emptyMap(), value = 5))
        appender.append(MeteringEvent.Decrement(nextId(), "pool.size", emptyMap(), value = 2))

        appender.create("latency", Kind.Histogram, unit = "ms")
        appender.append(MeteringEvent.Record(nextId(), "latency", mapOf("path" to "/a"), value = 0.5))
        appender.append(MeteringEvent.Record(nextId(), "latency", mapOf("path" to "/a"), value = 1.25))

        // Families are sorted by name; counters get `_total`; an UpDownCounter renders as a gauge;
        // units suffix the family name; no sample timestamps; the exposition ends with `# EOF`.
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE latency_ms histogram
            # UNIT latency_ms ms
            latency_ms_bucket{path="/a",le="+Inf"} 2
            latency_ms_sum{path="/a"} 1.75
            latency_ms_count{path="/a"} 2
            # TYPE pool_size gauge
            pool_size{} 3
            # TYPE requests counter
            # HELP requests Total requests.
            requests_total{path="/a"} 2
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun emptyRegistryRendersJustEof() = runTest {
        val appender = SimpleMeteringCollectorAppender()
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo("# EOF\n")
    }

    @Test
    fun eachTagSetGetsItsOwnSeriesUnderASingleHeader() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("hits", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "hits", mapOf("region" to "eu"), value = 1))
        appender.append(MeteringEvent.Increment(nextId(), "hits", mapOf("region" to "us"), value = 2))
        appender.append(MeteringEvent.Increment(nextId(), "hits", emptyMap(), value = 3))

        // The order of series within a family is hash-based (`sortKey()`), so assert the family
        // block's shape without depending on it: one header, the three series, then `# EOF`.
        val lines = appender.toOpenMetricsLineFormatString().lines()
        assertThat(lines.size).isEqualTo(6) // header + 3 series + "# EOF" + trailing newline
        assertThat(lines.first()).isEqualTo("# TYPE hits counter")
        assertThat(lines.filter { it.startsWith("hits_total") }.sorted()).isEqualTo(
            listOf(
                """hits_total{region="eu"} 1""",
                """hits_total{region="us"} 2""",
                "hits_total{} 3",
            )
        )
        assertThat(lines[4]).isEqualTo("# EOF")
    }

    // --- Aggregation semantics per instrument kind -----------------------------------------------

    @Test
    fun counterSetOverwritesAndIncrementsAccumulate() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("config", Kind.Counter)
        appender.append(MeteringEvent.Set(nextId(), "config", emptyMap(), value = 10))
        appender.append(MeteringEvent.Increment(nextId(), "config", emptyMap(), value = 5))
        appender.append(MeteringEvent.Set(nextId(), "config", emptyMap(), value = 3))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE config counter
            config_total{} 3
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun upDownCounterSupportsSetAndCanGoNegative() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // A Decrement may be the series' first event: the aggregate starts at 0 and goes negative.
        appender.create("drain", Kind.UpDownCounter)
        appender.append(MeteringEvent.Decrement(nextId(), "drain", emptyMap(), value = 5))

        // Set then a Double decrement: the accumulator promotes and keeps the fraction.
        appender.create("level", Kind.UpDownCounter)
        appender.append(MeteringEvent.Set(nextId(), "level", emptyMap(), value = 10))
        appender.append(MeteringEvent.Decrement(nextId(), "level", emptyMap(), value = 12.5))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE drain gauge
            drain{} -5
            # TYPE level gauge
            level{} -2.5
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun gaugeKeepsOnlyTheLatestRecordedValue() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("temperature", Kind.Gauge)
        appender.append(MeteringEvent.Record(nextId(), "temperature", emptyMap(), value = 3))
        appender.append(MeteringEvent.Record(nextId(), "temperature", emptyMap(), value = 6))
        appender.append(MeteringEvent.Record(nextId(), "temperature", emptyMap(), value = -2))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE temperature gauge
            temperature{} -2
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun histogramAccumulatesCountAndSumAcrossRecords() = runTest {
        // No finite buckets: only the implicit `+Inf` bucket plus `_sum`/`_count` remain.
        val appender = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = emptyList())

        appender.create("sizes", Kind.Histogram)
        appender.append(MeteringEvent.Record(nextId(), "sizes", emptyMap(), value = 0.5))
        appender.append(MeteringEvent.Record(nextId(), "sizes", emptyMap(), value = 1.5))
        appender.append(MeteringEvent.Record(nextId(), "sizes", emptyMap(), value = 2.25))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE sizes histogram
            sizes_bucket{le="+Inf"} 3
            sizes_sum{} 4.25
            sizes_count{} 3
            # EOF

            """.trimIndent()
        )
    }

    // --- Numeric type handling --------------------------------------------------------------------

    @Test
    fun mixedNumericTypesPromoteToTheWiderType() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // A Double accumulator plus an Int increment must not truncate the fraction.
        appender.create("fraction", Kind.Counter)
        appender.append(MeteringEvent.Set(nextId(), "fraction", emptyMap(), value = 10.5))
        appender.append(MeteringEvent.Increment(nextId(), "fraction", emptyMap(), value = 1))

        // A Long accumulator past Int.MAX_VALUE plus an Int increment must not wrap negative.
        appender.create("big", Kind.Counter)
        appender.append(MeteringEvent.Set(nextId(), "big", emptyMap(), value = 3_000_000_000L))
        appender.append(MeteringEvent.Increment(nextId(), "big", emptyMap(), value = 1))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE big counter
            big_total{} 3000000001
            # TYPE fraction counter
            fraction_total{} 11.5
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun numericPromotionFollowsTheWideningLadder() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // Int -> Long -> Float -> Double: each event widens the accumulator, never narrows it.
        appender.create("ladder", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "ladder", emptyMap(), value = 1))
        appender.append(MeteringEvent.Increment(nextId(), "ladder", emptyMap(), value = 2L))
        appender.append(MeteringEvent.Increment(nextId(), "ladder", emptyMap(), value = 0.5f))
        appender.append(MeteringEvent.Increment(nextId(), "ladder", emptyMap(), value = 0.25))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE ladder counter
            ladder_total{} 3.75
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun incrementAndDecrementSupportAllNumberTypes() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("short.and.byte", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "short.and.byte", emptyMap(), value = 1.toShort()))
        appender.append(MeteringEvent.Increment(nextId(), "short.and.byte", emptyMap(), value = 2.toByte()))

        appender.create("up.and.down", Kind.UpDownCounter)
        appender.append(MeteringEvent.Increment(nextId(), "up.and.down", emptyMap(), value = 5.toShort()))
        appender.append(MeteringEvent.Decrement(nextId(), "up.and.down", emptyMap(), value = 2.toByte()))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE short_and_byte counter
            short_and_byte_total{} 3
            # TYPE up_and_down gauge
            up_and_down{} 3
            # EOF

            """.trimIndent()
        )
    }

    // --- Registry behavior ------------------------------------------------------------------------

    @Test
    fun distinctSeriesWithCollidingHashKeysAreNotMerged() = runTest {
        // "Aa" and "BB" have identical string hash codes, so a registry keyed by an Int hash of
        // name+tags (as the since-removed `MeteringEvent.key()` did) folded both counters
        // into one series. The (name, tags) composite key must keep them apart.
        val appender = SimpleMeteringCollectorAppender()

        appender.create("Aa", Kind.Counter)
        appender.create("BB", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "Aa", emptyMap(), value = 1))
        appender.append(MeteringEvent.Increment(nextId(), "BB", emptyMap(), value = 2))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE Aa counter
            Aa_total{} 1
            # TYPE BB counter
            BB_total{} 2
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun valueEventsForUnregisteredInstrumentsAreDropped() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // No CreateInstrument for "ghost" yet: the increment has no metadata and is dropped.
        appender.append(MeteringEvent.Increment(nextId(), "ghost", emptyMap(), value = 7))
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo("# EOF\n")

        // Once registered, subsequent events aggregate (the dropped one stays dropped).
        appender.create("ghost", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "ghost", emptyMap(), value = 1))
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE ghost counter
            ghost_total{} 1
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun firstCreateInstrumentWinsAndReRegistrationIsIgnored() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("dup", Kind.Counter, description = "first")
        appender.create("dup", Kind.Gauge, unit = "ms", description = "second")
        appender.append(MeteringEvent.Increment(nextId(), "dup", emptyMap(), value = 1))

        // The second registration is ignored entirely: kind, unit and description stay first's.
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE dup counter
            # HELP dup first
            dup_total{} 1
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun mismatchedEventKindsDoNotRegisterPhantomSeries() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // "shared" is registered first as a Counter (first CreateInstrument wins); a Record event
        // against it must be dropped without leaving a phantom zero-valued counter series behind.
        appender.create("shared", Kind.Counter)
        appender.append(MeteringEvent.Record(nextId(), "shared", emptyMap(), value = 42))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo("# EOF\n")
    }

    @Test
    fun allMismatchedEventKindCombinationsAreDropped() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("c", Kind.Counter)
        appender.create("ud", Kind.UpDownCounter)
        appender.create("g", Kind.Gauge)
        appender.create("h", Kind.Histogram)

        // Set/Increment apply only to counters, Decrement only to up-down counters, and Record
        // only to gauges/histograms — every combination below is invalid and must leave no trace.
        appender.append(MeteringEvent.Set(nextId(), "g", emptyMap(), value = 1))
        appender.append(MeteringEvent.Set(nextId(), "h", emptyMap(), value = 1))
        appender.append(MeteringEvent.Increment(nextId(), "g", emptyMap(), value = 1))
        appender.append(MeteringEvent.Increment(nextId(), "h", emptyMap(), value = 1))
        appender.append(MeteringEvent.Decrement(nextId(), "c", emptyMap(), value = 1))
        appender.append(MeteringEvent.Decrement(nextId(), "g", emptyMap(), value = 1))
        appender.append(MeteringEvent.Decrement(nextId(), "h", emptyMap(), value = 1))
        appender.append(MeteringEvent.Record(nextId(), "c", emptyMap(), value = 1))
        appender.append(MeteringEvent.Record(nextId(), "ud", emptyMap(), value = 1))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo("# EOF\n")
    }

    @Test
    fun mismatchedEventsDoNotMutateExistingSeries() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("hits", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "hits", emptyMap(), value = 1))

        // The series already exists: mismatched events must neither mutate nor replace it.
        appender.append(MeteringEvent.Record(nextId(), "hits", emptyMap(), value = 99))
        appender.append(MeteringEvent.Decrement(nextId(), "hits", emptyMap(), value = 1))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE hits counter
            hits_total{} 1
            # EOF

            """.trimIndent()
        )
    }

    // --- Name/label sanitization and escaping ------------------------------------------------------

    @Test
    fun escapesLabelValuesAndHelpTextAndSanitizesNames() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("weird-name.metric", Kind.Counter, description = "line1\nline2 \\ backslash")
        appender.append(
            MeteringEvent.Increment(
                id = nextId(),
                name = "weird-name.metric",
                tags = mapOf("http.method" to "GET \"quoted\" \\ line\nbreak"),
                value = 1,
            )
        )

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE weird_name_metric counter
            # HELP weird_name_metric line1\nline2 \\ backslash
            weird_name_metric_total{http_method="GET \"quoted\" \\ line\nbreak"} 1
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun sanitizesLeadingDigitsColonsAndUnitCharacters() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        // A leading digit is prefixed with `_`.
        appender.create("9lives", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "9lives", emptyMap(), value = 1))

        // Colons are legal in metric names and preserved.
        appender.create("app:requests", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "app:requests", emptyMap(), value = 1))

        // Units are sanitized before suffixing the family name; label names get the same
        // treatment as metric names (illegal characters -> `_`, leading digit prefixed).
        appender.create("speed", Kind.Gauge, unit = "m/s")
        appender.append(MeteringEvent.Record(nextId(), "speed", mapOf("1st" to "x"), value = 5))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE _9lives counter
            _9lives_total{} 1
            # TYPE app:requests counter
            app:requests_total{} 1
            # TYPE speed_m_s gauge
            # UNIT speed_m_s m_s
            speed_m_s{_1st="x"} 5
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun tagValuesRenderViaTheirToString() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("tagged", Kind.Gauge)
        appender.append(
            MeteringEvent.Record(
                id = nextId(),
                name = "tagged",
                tags = mapOf("count" to 42, "flag" to true, "ratio" to 1.5),
                value = 1,
            )
        )

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE tagged gauge
            tagged{count="42",flag="true",ratio="1.5"} 1
            # EOF

            """.trimIndent()
        )
    }

    // --- Family naming: `_total` and unit suffixes --------------------------------------------------

    @Test
    fun counterAlreadyNamedWithTotalSuffixIsNotDoubled() = runTest {
        val appender = SimpleMeteringCollectorAppender()

        appender.create("errors_total", Kind.Counter)
        appender.append(MeteringEvent.Increment(nextId(), "errors_total", emptyMap(), value = 1))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE errors counter
            errors_total{} 1
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun unitAlreadySuffixedOnTheNameIsNotDoubled() = runTest {
        val appender = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = emptyList())

        // "req_ms" already ends with the unit: the family name must not become "req_ms_ms".
        // Also pins the metadata block order: TYPE, then UNIT, then HELP.
        appender.create("req_ms", Kind.Histogram, unit = "ms", description = "Request latency.")
        appender.append(MeteringEvent.Record(nextId(), "req_ms", emptyMap(), value = 1.5))

        appender.create("traffic", Kind.Counter, unit = "bytes", description = "Total traffic.")
        appender.append(MeteringEvent.Increment(nextId(), "traffic", emptyMap(), value = 5))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE req_ms histogram
            # UNIT req_ms ms
            # HELP req_ms Request latency.
            req_ms_bucket{le="+Inf"} 1
            req_ms_sum{} 1.5
            req_ms_count{} 1
            # TYPE traffic_bytes counter
            # UNIT traffic_bytes bytes
            # HELP traffic_bytes Total traffic.
            traffic_bytes_total{} 5
            # EOF

            """.trimIndent()
        )
    }

    // --- Histogram specifics ------------------------------------------------------------------------

    @Test
    fun histogramDropsUserTagNamedLe() = runTest {
        val appender = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = listOf(2.5))

        appender.create("latency", Kind.Histogram)
        appender.append(
            MeteringEvent.Record(nextId(), "latency", mapOf("le" to "user-value", "path" to "/a"), value = 1.5)
        )

        // The reserved `le` tag is dropped from all series; each bucket keeps its own `le`.
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE latency histogram
            latency_bucket{path="/a",le="2.5"} 1
            latency_bucket{path="/a",le="+Inf"} 1
            latency_sum{path="/a"} 1.5
            latency_count{path="/a"} 1
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun histogramFoldsObservationsIntoCumulativeDefaultBuckets() = runTest {
        // The default constructor applies the OTel default (millisecond-oriented) boundaries.
        val appender = SimpleMeteringCollectorAppender()

        appender.create("latency", Kind.Histogram, unit = "ms")
        // One observation per interesting region; the last exceeds the top boundary and lands
        // only in the implicit `+Inf` bucket.
        listOf(3.0, 7.5, 40.0, 900.0, 20_000.0).forEach {
            appender.append(MeteringEvent.Record(nextId(), "latency", emptyMap(), value = it))
        }

        // Buckets are cumulative and the `+Inf` bucket always equals `_count`.
        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE latency_ms histogram
            # UNIT latency_ms ms
            latency_ms_bucket{le="5.0"} 1
            latency_ms_bucket{le="10.0"} 2
            latency_ms_bucket{le="25.0"} 2
            latency_ms_bucket{le="50.0"} 3
            latency_ms_bucket{le="75.0"} 3
            latency_ms_bucket{le="100.0"} 3
            latency_ms_bucket{le="250.0"} 3
            latency_ms_bucket{le="500.0"} 3
            latency_ms_bucket{le="750.0"} 3
            latency_ms_bucket{le="1000.0"} 4
            latency_ms_bucket{le="2500.0"} 4
            latency_ms_bucket{le="5000.0"} 4
            latency_ms_bucket{le="7500.0"} 4
            latency_ms_bucket{le="10000.0"} 4
            latency_ms_bucket{le="+Inf"} 5
            latency_ms_sum{} 20950.5
            latency_ms_count{} 5
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun histogramBoundariesCanBeOverriddenPerInstrumentName() = runTest {
        // Overrides are keyed by the instrument name as registered with the Meter; instruments
        // without an entry fall back to the appender-wide default.
        val appender = SimpleMeteringCollectorAppender(
            defaultHistogramBucketBoundaries = listOf(1.0),
            histogramBucketBoundaries = mapOf("sizes" to listOf(10.0, 100.0)),
        )

        appender.create("sizes", Kind.Histogram)
        appender.append(MeteringEvent.Record(nextId(), "sizes", emptyMap(), value = 5))
        appender.append(MeteringEvent.Record(nextId(), "sizes", emptyMap(), value = 50.5))

        appender.create("other", Kind.Histogram)
        appender.append(MeteringEvent.Record(nextId(), "other", emptyMap(), value = 0.5))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE other histogram
            other_bucket{le="1.0"} 1
            other_bucket{le="+Inf"} 1
            other_sum{} 0.5
            other_count{} 1
            # TYPE sizes histogram
            sizes_bucket{le="10.0"} 1
            sizes_bucket{le="100.0"} 2
            sizes_bucket{le="+Inf"} 2
            sizes_sum{} 55.5
            sizes_count{} 2
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun histogramBucketUpperBoundsAreInclusive() = runTest {
        val appender = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = listOf(1.0, 2.5))

        // Observations exactly on a boundary land in that bucket (`le` = less-or-equal).
        appender.create("exact", Kind.Histogram)
        appender.append(MeteringEvent.Record(nextId(), "exact", emptyMap(), value = 1.0))
        appender.append(MeteringEvent.Record(nextId(), "exact", emptyMap(), value = 2.5))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE exact histogram
            exact_bucket{le="1.0"} 1
            exact_bucket{le="2.5"} 2
            exact_bucket{le="+Inf"} 2
            exact_sum{} 3.5
            exact_count{} 2
            # EOF

            """.trimIndent()
        )
    }

    @Test
    fun histogramBoundariesAreNormalizedBeforeUse() = runTest {
        // Unsorted, duplicated and non-finite boundaries: the appender sorts ascending,
        // deduplicates, and drops NaN/±Inf (the `+Inf` bucket is always emitted implicitly).
        val appender = SimpleMeteringCollectorAppender(
            defaultHistogramBucketBoundaries = listOf(
                5.0, 0.5, Double.NaN, 5.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            ),
        )

        appender.create("normalized", Kind.Histogram)
        appender.append(MeteringEvent.Record(nextId(), "normalized", emptyMap(), value = 0.25))

        assertThat(appender.toOpenMetricsLineFormatString()).isEqualTo(
            """
            # TYPE normalized histogram
            normalized_bucket{le="0.5"} 1
            normalized_bucket{le="5.0"} 1
            normalized_bucket{le="+Inf"} 1
            normalized_sum{} 0.25
            normalized_count{} 1
            # EOF

            """.trimIndent()
        )
    }

    // --- Integration through the RootLogger pipeline -----------------------------------------------

    @Test
    fun collectsEventsDeliveredThroughTheRootLoggerPipeline() = runTest {
        val saved = RootLogger.Metering.appenders.all()
        RootLogger.Metering.appenders.unregisterAll()
        // Register the collector before the capturer: RootLogger delivers each event to the
        // appenders in registration order, so once the capturer sees an event the collector has
        // already folded it. (Buckets are covered by the histogram tests; disabled here.)
        val collector = SimpleMeteringCollectorAppender(defaultHistogramBucketBoundaries = emptyList())
        val capturing = CapturingMeteringAppender()
        RootLogger.Metering.appenders.register(collector)
        RootLogger.Metering.appenders.register(capturing)
        try {
            val meter = SimpleMeter("collector.pipeline", Level.INFO)
            val requests = meter.counter<Long>("pipeline.requests", description = "Requests.")
            val inflight = meter.upDownCounter<Int>("pipeline.inflight")
            val latency = meter.histogram<Double>("pipeline.latency", unit = "ms")

            requests.increment(2L, "path" to "/a")
            inflight.increment(3)
            inflight.decrement(1)
            latency.record(1.5, "path" to "/a")
            latency.record(2.75, "path" to "/a")

            // The metering channel is FIFO: awaiting both latency records (the last events sent)
            // guarantees everything before them has reached the collector too.
            capturing.awaitValue("pipeline.latency")
            capturing.awaitValue("pipeline.latency")

            assertThat(collector.toOpenMetricsLineFormatString()).isEqualTo(
                """
                # TYPE pipeline_inflight gauge
                pipeline_inflight{} 2
                # TYPE pipeline_latency_ms histogram
                # UNIT pipeline_latency_ms ms
                pipeline_latency_ms_bucket{path="/a",le="+Inf"} 2
                pipeline_latency_ms_sum{path="/a"} 4.25
                pipeline_latency_ms_count{path="/a"} 2
                # TYPE pipeline_requests counter
                # HELP pipeline_requests Requests.
                pipeline_requests_total{path="/a"} 2
                # EOF

                """.trimIndent()
            )
        } finally {
            RootLogger.Metering.appenders.unregisterAll()
            saved.forEach { RootLogger.Metering.appenders.register(it) }
        }
    }

    // --- Numeric widening & concurrent scrapes ---------------------------------------------------

    @Test
    fun intFedCounter_widensToLong_insteadOfWrappingAtIntMax() = runTest {
        val appender = SimpleMeteringCollectorAppender()
        appender.create("big_hits", Kind.Counter)

        appender.append(MeteringEvent.Increment(nextId(), "big_hits", emptyMap(), value = Int.MAX_VALUE))
        appender.append(MeteringEvent.Increment(nextId(), "big_hits", emptyMap(), value = 1))

        // 2147483648 = Int.MAX_VALUE + 1: the old Int accumulator wrapped to -2147483648.
        assertThat(appender.toOpenMetricsLineFormatString()).contains("big_hits_total{} 2147483648")
    }

    @Test
    fun intFedUpDownCounter_widensToLong_insteadOfWrappingAtIntMin() = runTest {
        val appender = SimpleMeteringCollectorAppender()
        appender.create("big_swing", Kind.UpDownCounter)

        appender.append(MeteringEvent.Set(nextId(), "big_swing", emptyMap(), value = Int.MIN_VALUE))
        appender.append(MeteringEvent.Decrement(nextId(), "big_swing", emptyMap(), value = 1))

        // -2147483649 = Int.MIN_VALUE - 1: the old Int accumulator wrapped to +2147483647.
        assertThat(appender.toOpenMetricsLineFormatString()).contains("big_swing{} -2147483649")
    }

    @Test
    fun exposition_isRenderableWhileEventsAreBeingAppended() = runTest {
        // Scrape-race regression: rendering used to iterate plain HashMaps that `append` mutates,
        // so a scrape concurrent with the consumer could crash (ConcurrentModificationException)
        // or observe a torn aggregate. With copy-on-write snapshots and immutable aggregates the
        // renderer must always see a consistent state.
        val appender = SimpleMeteringCollectorAppender()
        appender.create("race_hits", Kind.Counter)
        appender.create("race_lat", Kind.Histogram)

        withContext(Dispatchers.Default) {
            val writer = launch {
                repeat(2_000) { i ->
                    appender.append(MeteringEvent.Increment(nextId(), "race_hits", emptyMap(), value = 1))
                    appender.append(MeteringEvent.Record(nextId(), "race_lat", emptyMap(), value = i))
                }
            }
            // Render continuously while the writer folds events (yield keeps single-threaded
            // targets fair). Every snapshot must be internally consistent: the histogram's
            // `+Inf` bucket and `_count` lines render the same immutable aggregate (bucket
            // counts included, exercising the copy-on-record path), so they can never disagree.
            while (writer.isActive) {
                val exposition = appender.toOpenMetricsLineFormatString()
                assertThat(exposition.lineValue("""race_lat_bucket{le="+Inf"}"""))
                    .isEqualTo(exposition.lineValue("race_lat_count"))
                yield()
            }
            writer.join()
        }

        val exposition = appender.toOpenMetricsLineFormatString()
        assertThat(exposition).contains("race_hits_total{} 2000")
        assertThat(exposition).contains("race_lat_count{} 2000")
    }

    /** The value (the text after the last space) of the first exposition line starting with [prefix]. */
    private fun String.lineValue(prefix: String): String? =
        lineSequence().firstOrNull { it.startsWith(prefix) }?.substringAfterLast(' ')
}
