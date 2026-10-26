plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    alias(libs.plugins.kover)
}

// The same bytecode target as :app, which runs this code on Android.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // api: the app builds the one client with ArchiveHttp and hands it to IndexFetcher.
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// SeedMain is the build's entry point, run by `generateIndexSeed` against the live hosts, and
// excluded for that reason: what it does is fetch and write, which the tests cover through
// IndexFetcher and SeedFormat.
kover {
    reports {
        filters {
            excludes {
                classes("com.cooper.binge.companion.archive.index.SeedMainKt")
            }
        }
        // 95, measured at 98.8% when the module landed, the same floor as :app's.
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
