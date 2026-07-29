import io.github.smyrgeorge.log4k.examples.CallSiteCompilerPlugin
import io.github.smyrgeorge.log4k.examples.Classic
import io.github.smyrgeorge.log4k.examples.LoggedCompilerPlugin
import io.github.smyrgeorge.log4k.examples.TimedCompilerPlugin
import io.github.smyrgeorge.log4k.examples.TracedCompilerPlugin

fun main() {
    Classic.run()
    TracedCompilerPlugin.run()
    TimedCompilerPlugin.run()
    LoggedCompilerPlugin.run()
    CallSiteCompilerPlugin.run()
}
