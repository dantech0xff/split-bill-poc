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

// The upload key never lives in the repository. A release build is signed only when all four
// variables are set, which only the release workflow does. Everywhere else it comes out unsigned.
val uploadKey: Map<String, String> = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .mapNotNull { name -> providers.environmentVariable("SPLITVIBE_UPLOAD_$name").orNull?.let { name to it } }
    .toMap()
val hasUploadKey = uploadKey.size == 4

android {
    namespace = "com.dantech.splitvibe"
    // Compose 1.12 and material3 1.5 need the Android 17 SDK to compile against.
    // The app still targets API 36, so none of Android 17's behaviour changes apply yet.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dantech.splitvibe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"\"")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadKey.getValue("KEYSTORE_FILE"))
                storePassword = uploadKey.getValue("KEYSTORE_PASSWORD")
                keyAlias = uploadKey.getValue("KEY_ALIAS")
                keyPassword = uploadKey.getValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"$debugAnthropicApiKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    // `dev` installs next to the real app under its own id, so test data never mixes with real data.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "environment"
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
        unitTests.all { test ->
            // Screenshot tests compare the screens with the images in src/test/screenshots.
            // After an intended visual change, record new ones with -Pscreenshots=record.
            val screenshots = providers.gradleProperty("screenshots").getOrElse("verify")
            test.systemProperty("roborazzi.test.$screenshots", "true")
            // Draws shadows and other hardware-rendered effects the way a device does.
            test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
        // An emulator that Gradle downloads, starts and stops itself for the tests in src/androidTest:
        // ./gradlew :app:pixel6Api34ProdDebugAndroidTest
        managedDevices {
            localDevices {
                create("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
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
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

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
    testImplementation(libs.roborazzi)
    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Provides the empty activity that hosts composables in UI tests.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
