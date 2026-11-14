plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.dantech.splitvibe.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Baseline profiles can be recorded from Android 9 on.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // The same flavors as the app, so each one is measured as itself.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

// Recording uses the emulator that Gradle manages, so it gives the same result on any machine.
// Measurements belong on a real phone: run the benchmarks there with connectedProdBenchmarkReleaseAndroidTest.
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

// Tells the tests which app to drive: the id of the flavor that is being recorded or measured.
androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { apks ->
                checkNotNull(artifactsLoader.load(apks)) { "The app under test has not been built" }.applicationId
            },
        )
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
