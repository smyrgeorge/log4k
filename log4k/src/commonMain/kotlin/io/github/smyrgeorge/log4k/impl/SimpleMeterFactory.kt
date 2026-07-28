package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeterFactory
import io.github.smyrgeorge.log4k.RootLogger

/**
 * The default [MeterFactory]: creates a [SimpleMeter] whose level is a **snapshot** of
 * [RootLogger.Metering.level] taken at creation time. Because instances are cached by name,
 * changing [RootLogger.Metering.level] afterwards affects only meters created from then on —
 * adjust existing ones through [Meter.registry] (e.g. `Meter.registry.setLevel(name, level)`).
 */
class SimpleMeterFactory : MeterFactory() {
    override fun create(name: String): Meter = SimpleMeter(name, RootLogger.Metering.level)
}
