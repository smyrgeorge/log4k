package io.github.smyrgeorge.log4k.multiplatform

object Utils {
    val allTargets: List<String> = listOf(
        "iosArm64",
        "iosX64",
        "iosSimulatorArm64",
        "androidNativeX64",
        "androidNativeArm64",
        "macosArm64",
        "macosX64",
        "linuxArm64",
        "linuxX64",
        "mingwX64",
        "jvm",
        "js",
        "wasmJs",
        "wasmWasi",
    )
}
