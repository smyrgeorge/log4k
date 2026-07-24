package io.github.smyrgeorge.log4k.impl.registry

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.test.Test

/** A minimal [CollectorRegistry.Collector] test double, relying on the interface's default mute logic. */
private class FakeCollector(
    override val name: String,
    override var level: Level = Level.INFO,
) : CollectorRegistry.Collector {
    override var levelBeforeMute: Level = level
}

/**
 * Tests for [CollectorRegistry] and its [CollectorRegistry.Collector] default mute behavior. Each test
 * uses a fresh registry, so there is no shared state to isolate.
 */
class CollectorRegistryTests {

    private class CollectorSample

    // --- registry: lookup & registration -------------------------------------------------------

    @Test
    fun get_unknownName_returnsNull() {
        assertThat(CollectorRegistry<FakeCollector>().get("missing")).isNull()
    }

    @Test
    fun register_thenGetByName_returnsInstance() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector("a")
        registry.register(collector)

        assertThat(registry.get("a")).isSameInstanceAs(collector)
    }

    @Test
    fun register_sameName_overwritesPrevious() {
        val registry = CollectorRegistry<FakeCollector>()
        val first = FakeCollector("a")
        val second = FakeCollector("a")
        registry.register(first)
        registry.register(second)

        assertThat(registry.get("a")).isSameInstanceAs(second)
    }

    @Test
    fun getByClass_delegatesToToName() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector(CollectorSample::class.toName())
        registry.register(collector)

        assertThat(registry.get(CollectorSample::class)).isSameInstanceAs(collector)
    }

    @Test
    fun register_forPreMutedName_mutesTheCollector() {
        val registry = CollectorRegistry<FakeCollector>()
        registry.mute("a") // no collector yet -> only the mute set records it

        val collector = FakeCollector("a", Level.INFO)
        registry.register(collector)

        assertThat(collector.isMuted()).isTrue()
        assertThat(collector.level).isEqualTo(Level.OFF)
    }

    // --- registry: mute / unmute / setLevel ----------------------------------------------------

    @Test
    fun mute_registeredCollector_setsLevelOffAndRecordsInSet() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector("a", Level.INFO)
        registry.register(collector)

        registry.mute("a")

        assertThat(collector.isMuted()).isTrue()
        assertThat(collector.level).isEqualTo(Level.OFF)
        assertThat(registry.isMuted("a")).isTrue()
    }

    @Test
    fun unmute_restoresLevelAndClearsSet() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector("a", Level.INFO)
        registry.register(collector)

        registry.mute("a")
        registry.unmute("a")

        assertThat(collector.level).isEqualTo(Level.INFO)
        assertThat(collector.isMuted()).isFalse()
        assertThat(registry.isMuted("a")).isFalse()
    }

    @Test
    fun mute_unknownName_recordsInSetWithoutError() {
        val registry = CollectorRegistry<FakeCollector>()

        registry.mute("ghost") // no collector registered

        assertThat(registry.isMuted("ghost")).isTrue()
        registry.unmute("ghost")
        assertThat(registry.isMuted("ghost")).isFalse()
    }

    @Test
    fun isMuted_tracksTheMuteSet_notTheCollectorLevel() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector("a", Level.OFF) // collector is OFF on its own
        registry.register(collector)

        assertThat(collector.isMuted()).isTrue()       // the collector reports muted (level == OFF)
        assertThat(registry.isMuted("a")).isFalse()     // but the registry mute set never recorded it
    }

    @Test
    fun setLevel_updatesRegisteredCollector() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector("a", Level.INFO)
        registry.register(collector)

        registry.setLevel("a", Level.ERROR)

        assertThat(collector.level).isEqualTo(Level.ERROR)
    }

    @Test
    fun setLevel_unknownName_isNoOp() {
        val registry = CollectorRegistry<FakeCollector>()
        registry.setLevel("missing", Level.ERROR) // must not throw
        assertThat(registry.get("missing")).isNull()
    }

    @Test
    fun muteByClass_delegatesToToName() {
        val registry = CollectorRegistry<FakeCollector>()
        val collector = FakeCollector(CollectorSample::class.toName(), Level.INFO)
        registry.register(collector)

        registry.mute(CollectorSample::class)

        assertThat(collector.isMuted()).isTrue()
        assertThat(registry.isMuted(CollectorSample::class)).isTrue()
    }

    // --- Collector default mute/unmute ---------------------------------------------------------

    @Test
    fun collector_muteThenUnmute_roundTripsLevel() {
        val collector = FakeCollector("a", Level.WARN)

        collector.mute()
        assertThat(collector.level).isEqualTo(Level.OFF)
        assertThat(collector.isMuted()).isTrue()

        collector.unmute()
        assertThat(collector.level).isEqualTo(Level.WARN)
        assertThat(collector.isMuted()).isFalse()
    }

    @Test
    fun collector_constructedAtOff_reportsMuted() {
        assertThat(FakeCollector("a", Level.OFF).isMuted()).isTrue()
    }
}
