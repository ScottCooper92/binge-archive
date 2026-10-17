import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)
    id("binge.gates.detekt")
    alias(libs.plugins.kover)
    alias(libs.plugins.screenshot)
    id("binge.gates.tv-material-separation")
    id("binge.gates.translation-staleness")
}

// The exported schema is committed: a change to a table is a decision made in review.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "io.github.scottcooper92.binge.archive"
    compileSdk = 37

    // What makes AGP create the screenshotTest source set. The matching flag is in gradle.properties;
    // both have to be present or the frames compile nowhere and the task does not exist.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "io.github.scottcooper92.binge.archive"
        minSdk = 26
        targetSdk = 36
        // A release workflow overrides both from the tag it builds; a local build keeps the placeholders.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    }

    buildFeatures {
        // BuildConfig.VERSION_NAME goes in the User-Agent Wikimedia asks every client to send.
        buildConfig = true
        compose = true
    }

    // Generates the locale config from the values-<lang> directories, so Android 13+ offers this app
    // in the per-app language picker.
    androidResources { generateLocaleConfig = true }

    // A locale is complete or it does not exist: the alternative is Android's per-string fallback,
    // half a screen in each language. Adding an English string means adding its Spanish in the same
    // change. UnusedResources is pinned too: a removed call site can otherwise leave its string behind.
    lint {
        error +=
            setOf(
                "MissingTranslation",
                "ExtraTranslation",
                "MissingQuantity",
                "UnusedQuantity",
                "StringFormatMatches",
                "StringFormatCount",
                "UnusedResources",
            )
    }

    // The release keystore is never in the repository: the values come from keystore.properties at
    // the root (gitignored) or from the environment. Without them the release build has no signing
    // config, so it still builds everywhere, as an unsigned APK.
    val keystoreProperties =
        Properties().apply {
            val file = rootDir.resolve("keystore.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }

    fun signingValue(key: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(key)
    val releaseStoreFile = signingValue("RELEASE_STORE_FILE")

    signingConfigs {
        // Committed rather than left to AGP's implicit debug key, which each machine generates for
        // itself: a debug build from one machine would then not install over one from another.
        // Standard AOSP debug credentials; there is nothing secret in a debug key.
        getByName("debug") {
            storeFile = rootDir.resolve("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootDir.resolve(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A package of its own, so a debug build installs beside a release one instead of over it.
            applicationIdSuffix = ".debug"
        }
        release {
            // One switch for code shrinking, resource shrinking and the default Android keep rules.
            optimization { enable = true }
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        // Robolectric hosts Compose on the JVM, against screens built from stringResource, so the
        // merged resources have to be on the unit-test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

// A reworded English string is the one translation drift lint cannot see, so the hash of every
// translated source string is committed at the root and `check` fails when one moves. Re-read the
// translation it names, then re-stamp with `updateTranslationHashes`. The gate is binge-ci's.
translationStaleness {
    includes.set(listOf("app/src/*/res/values/strings.xml", "app/src/*/res/values-*/strings.xml"))
}

// Material 3 and tv-material each ship a MaterialTheme under the same simple name, so the wrong
// import compiles and renders subtly wrong. binge-ci's gate keeps `ui/tv` on tv-material and
// everything else off it. ArchiveTheme.kt is the one crossing: it projects this app's colours into
// tv-material's.
tvMaterialSeparation {
    sources.from(fileTree("src") { include("**/*.kt") })
    allowlist.add("app/src/main/kotlin/io/github/scottcooper92/binge/archive/theme/ArchiveTheme.kt")
    themeTypeNames.addAll("ArchiveDarkColorScheme", "ArchiveLightColorScheme")
}

dependencies {
    implementation(project(":index"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.binge.companion.sdk)
    implementation(libs.binge.companion.contracts)
    implementation(libs.binge.designsystem)
    // The television surface in ui/tv. tv-material and the phone's Material 3 are never mixed in one file.
    implementation(libs.binge.designsystem.tv)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // The Service is driven over an in-process channel: the whole contract, no device.
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // The frames render against the same Compose the app ships, and the validation API is what
    // @PreviewTest resolves to.
    screenshotTestImplementation(platform(libs.compose.bom))
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}

/**
 * The seed index the APK ships, so a fresh install can answer before its first refresh. It runs
 * :index's own fetcher against Wikidata and the Archive, the same code the weekly refresh runs.
 *
 * Offline, or with either host down, it writes nothing and the build goes on; the app then starts
 * with an empty index. A seed younger than a week is reused rather than fetched again, and
 * `-Parchive.skipSeed` skips the fetch altogether.
 */
abstract class GenerateIndexSeed : JavaExec() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    override fun exec() {
        args(
            outputDirectory
                .file("index/seed.tsv")
                .get()
                .asFile.absolutePath,
            versionName.get(),
        )
        super.exec()
    }
}

val seedMaxAgeMillis = 7L * 24 * 60 * 60 * 1000
val generateIndexSeed =
    tasks.register<GenerateIndexSeed>("generateIndexSeed") {
        description = "Fetches the seed index the APK ships."
        classpath = project(":index").extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
        mainClass.set("io.github.scottcooper92.binge.archive.index.SeedMainKt")
        outputDirectory.set(layout.buildDirectory.dir("generated/indexSeed"))
        versionName.set(android.defaultConfig.versionName ?: "build")
        onlyIf("archive.skipSeed is not set") { !project.hasProperty("archive.skipSeed") }
        val seed = outputDirectory.file("index/seed.tsv")
        outputs.upToDateWhen { seed.get().asFile.let { it.exists() && System.currentTimeMillis() - it.lastModified() < seedMaxAgeMillis } }
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateIndexSeed, GenerateIndexSeed::outputDirectory)
    }
}

// detekt is binge-ci's gate: production `src/main` only, with type resolution against the debug
// compilation, against the root detekt.yml. There is no baseline: the gate is zero findings.

// The exception on a failed test, into the console and so into CI's own log. Gradle's default
// prints the exception's class and not its message.
tasks.withType<Test>().configureEach {
    // binge-companions#148's live measurement (measure/CoverageMeasurement.kt), skipped unless asked for.
    providers.gradleProperty("archive.measure").orNull?.let { systemProperty("archive.measure", it) }
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // The screenshot render and validate tasks are Test tasks too, and they discover no JUnit tests.
    if (name.contains("ScreenshotTest")) {
        failOnNoDiscoveredTests = false
        maxParallelForks = 1
    } else {
        // Robolectric fetches its android-all jars itself, outside Gradle's repositories, so point it at
        // the same mirror as settings.gradle.kts: Central answers bursts from shared runners with a 429.
        systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
        systemProperty("robolectric.dependency.repo.id", "MavenCentralMirror")
    }
}

// The screenshot plugin does not wire itself into `check`, so without this `./gradlew build` would
// carry the frames and never compare them.
tasks.named("check") {
    dependsOn("validateDebugScreenshotTest")
}

// The gate measures logic. What is excluded is an Android entry point with nothing a JVM test can
// reach, or a composable: how a screen looks is the screenshot gate's question, not line coverage's.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*ComposableSingletons*",
                    "*PreviewData*",
                    "io.github.scottcooper92.binge.archive.MainActivity*",
                    // The exported Service: services() and hostPolicy() hand the SDK objects from the graph.
                    // What they choose is tested through ArchiveStreamService and selectHostPolicy.
                    "io.github.scottcooper92.binge.archive.service.ArchiveCompanionService",
                    // The Application and the hand-built graph: wiring, every member a constructed object.
                    "io.github.scottcooper92.binge.archive.ArchiveApp",
                    "io.github.scottcooper92.binge.archive.AppGraph*",
                    // Generated: Room's DAO and database, with their inner classes, the DAO's default-method
                    // holder, and BuildConfig.
                    "*_Impl*",
                    "*\$DefaultImpls",
                    "io.github.scottcooper92.binge.archive.BuildConfig",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        // 95, measured: the suite sat at 96.4% at the bootstrap and 98.8% when the index landed.
        // Chosen rather than inherited, with a little headroom for a class that is awkward to reach.
        // Ratchet it up as the resolver and the Service land; never lower it.
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
