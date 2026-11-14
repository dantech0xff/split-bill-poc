// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the Kotlin Android plugin is not applied.
// This classpath entry pins the Kotlin version AGP uses to the one in the version catalog.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Code style. ktlint runs as a plain Java program, so no extra Gradle plugin is involved.
// The rules it follows are in .editorconfig.
val ktlint: Configuration by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL)) }
    }
}

val kotlinFiles = listOf("**/src/**/*.kt", "**/*.gradle.kts")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks the Kotlin code style."
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    args(kotlinFiles)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fixes the Kotlin code style where it can and reports the rest."
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("--format") + kotlinFiles)
}
