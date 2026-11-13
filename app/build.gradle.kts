import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Optional developer convenience: put ANTHROPIC_API_KEY=sk-ant-... in local.properties (git-ignored)
// to pre-fill the key in debug builds. The key is then readable inside that debug APK, so never
// hand such an APK to anyone. Release builds never embed a key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val debugAnthropicApiKey: String = localProperties.getProperty("ANTHROPIC_API_KEY", "").trim()

android {
    namespace = "com.example.splitbill"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.splitbill"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"$debugAnthropicApiKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // POC only: sign release with the debug key so `assembleRelease` yields an installable APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Lets the UI flow run as a plain unit test (Robolectric) with the app's real resources.
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // Recorded real-world data (ML Kit output, a Claude answer) used by both local and on-device tests.
        getByName("test").resources.directories.add("src/sharedTest/resources")
        getByName("androidTest").resources.directories.add("src/sharedTest/resources")
    }

    packaging {
        resources {
            // Duplicate license/metadata files shipped by the Anthropic SDK's JVM dependencies.
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // On-device OCR: bundled Latin-script model, works offline without Google Play services downloads.
    implementation(libs.mlkit.text.recognition)

    // Official Anthropic SDK for the JVM (Kotlin uses the Java SDK).
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Provides the empty activity that hosts composables in UI tests.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
