// Root build file: plugin declarations only. Configuration lives in the module that needs it.
plugins {
    alias(libs.plugins.android.application) apply false
    // Declared and never applied: this puts the catalog's Kotlin on the build classpath for AGP's
    // built-in Kotlin support to use. See the note on `kotlin` in libs.versions.toml.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
