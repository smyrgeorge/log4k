import io.github.smyrgeorge.log4k.multiplatform.Utils

plugins {
    kotlin("multiplatform")
}

val enabledTargets = Utils.targetsOf(project)

kotlin {
    val availableTargets = mapOf(
        "macosArm64" to { macosArm64 { binaries { executable() } } },
        "jvm" to { jvm() },
    )

    enabledTargets.forEach {
        println("Enabling target $it")
        availableTargets[it]?.invoke()
    }

    applyDefaultHierarchyTemplate()
}
