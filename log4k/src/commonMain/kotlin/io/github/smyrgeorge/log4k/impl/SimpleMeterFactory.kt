package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Meter
import io.github.smyrgeorge.log4k.MeterFactory
import io.github.smyrgeorge.log4k.RootLogger

class SimpleMeterFactory : MeterFactory() {
    override fun create(name: String): Meter = SimpleMeter(name, RootLogger.Metering.level)
}
