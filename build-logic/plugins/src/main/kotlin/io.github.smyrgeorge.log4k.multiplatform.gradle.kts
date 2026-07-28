@file:OptIn(ExperimentalWasmDsl::class)

import io.github.smyrgeorge.log4k.multiplatform.Utils
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    val availableTargets = mapOf(
        "iosArm64" to { iosArm64() },
        "iosX64" to { iosX64() },
        "iosSimulatorArm64" to { iosSimulatorArm64() },
        "androidNativeArm64" to { androidNativeArm64() },
        "androidNativeX64" to { androidNativeX64() },
        "macosArm64" to { macosArm64() },
        "linuxArm64" to { linuxArm64() },
        "linuxX64" to { linuxX64() },
        "mingwX64" to { mingwX64() },
        "jvm" to {
            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        },
        "js" to {
            js {
                browser()
                nodejs {
                    testTask {
                        // Mocha's default per-test timeout is 2s, which flakes on loaded CI
                        // runners; kotlinx-coroutines' runTest guards against genuine hangs.
                        useMocha { timeout = "30s" }
                    }
                }
            }
        },
        "wasmJs" to {
            wasmJs {
                browser()
                nodejs()
            }
        },
        "wasmWasi" to {
            wasmWasi {
                nodejs()
            }
        },
    ).mapNotNull { (target, enable) ->
        // Ktor (the HTTP client used by log4k-integrations) does not support the wasmWasi target.
        if (project.name == "log4k-integrations" && target == "wasmWasi") null
        else target to enable
    }.toMap()

    Utils.allTargets.forEach {
        println("Enabling target $it")
        availableTargets[it]?.invoke()
    }

    applyDefaultHierarchyTemplate()
}
