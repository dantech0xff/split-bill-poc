import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The money logic and the data contracts: plain Kotlin with no Android dependency, so the rules
// that decide who owes what can be tested on any JVM and reused outside the app.
plugins {
    // The Kotlin Gradle plugin is already on the build classpath (see the root build file),
    // so it is applied here without a version.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    // Lint's checks of plain Kotlin code, which the app's lint runs with its own (checkDependencies).
    // Its check of API levels (NewApi) is not among them: lint runs it on Android modules only. The
    // plugin comes with the Android Gradle Plugin on the build classpath.
    id("com.android.lint")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
