// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the Kotlin Android plugin is not applied.
// This classpath entry pins the Kotlin version AGP uses to the one in the version catalog.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
