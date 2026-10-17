pluginManagement {
    // The shared Gradle gates come from binge-ci as source, through a submodule pinned to the release
    // the workflows also pin. Its plugins are applied by id with no version.
    includeBuild("binge-ci/gradle-plugins")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Google's mirror of Maven Central, asked first. Shared build hosts are rate-limited by Central
        // and get 429s mid-build; the mirror serves the same artifacts. Anything it lacks falls through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        // The portal before Central: some plugins (ktlint's, for one) are published only there.
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }

    // Catalog at the root rather than gradle/, matching binge-seerr, so a version bump applies to
    // both the same way.
    versionCatalogs {
        create("libs") { from(files("libs.versions.toml")) }
    }
}

rootProject.name = "binge-archive"

// The contracts and the SDK come from binge-companions as source, through a git submodule and a
// composite build. Gradle substitutes `io.github.scottcooper92:binge-companion-contracts` and
// `:binge-companion-sdk` with the included build's projects.
includeBuild("binge-companions")

// The shared design system arrives the same way: the theme and components come from
// binge-design-system as source. Gradle substitutes `com.binge:designsystem` and
// `com.binge:designsystem-tv` with the included build's projects.
includeBuild("design-system") {
    dependencySubstitution {
        substitute(module("com.binge:designsystem")).using(project(":designsystem"))
        substitute(module("com.binge:designsystem-tv")).using(project(":designsystem-tv"))
    }
}

include(":app")
// The index's data sources and seed format, as plain JVM code: the app's refresh and the build's
// seed task run the same fetchers and parsers.
include(":index")
