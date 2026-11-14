import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// Where the app reaches the AI proxy (see proxy/ and docs/proxy.md). No key of any kind is built into
// the app. local.properties (git-ignored) may set splitvibe.aiProxyUrl; a debug build otherwise uses
// `wrangler dev` on the computer, reached from the phone through `adb reverse tcp:8787 tcp:8787`.
// A release build must use https, and without the setting it is built with AI reading off.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val aiProxyUrl: String? = localProperties.getProperty("splitvibe.aiProxyUrl")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
// Something that is not an address, such as one with <your-subdomain> still in it, would only turn AI
// reading off once the app runs, so it stops every build.
aiProxyUrl?.let { url ->
    val address = runCatching { URI(url) }.getOrNull()
    check(address != null && address.scheme in setOf("http", "https") && !address.host.isNullOrEmpty()) {
        "splitvibe.aiProxyUrl must be an address such as https://split-vibe-proxy.example.workers.dev, but it is $url"
    }
}
val debugAiProxyUrl: String = aiProxyUrl ?: "http://127.0.0.1:8787"
val releaseAiProxyUrl: String = aiProxyUrl.orEmpty()
// Only a release build must use https, so only building one checks it: a plain-http address of a
// computer on the local network still serves debug builds and the tests.
val releaseAiProxyUrlProblem: String? = releaseAiProxyUrl.takeIf { it.isNotEmpty() && !it.startsWith("https://") }
    ?.let { "splitvibe.aiProxyUrl must start with https:// for release builds, but it is $it" }
tasks.matching { it.name.startsWith("pre") && it.name.endsWith("ReleaseBuild") }.configureEach {
    val problem = releaseAiProxyUrlProblem
    doFirst { problem?.let { throw GradleException(it) } }
}
// The app's web client ID at Google (public): Google's ID tokens for the app are made out to it, and
// the proxy checks that. Without it the app is built with signing in turned off.
val googleWebClientId: String = localProperties.getProperty("splitvibe.googleWebClientId")?.trim().orEmpty()
check(googleWebClientId.isEmpty() || Regex("[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com").matches(googleWebClientId)) {
    "splitvibe.googleWebClientId must be a web client ID such as 1234-abcd.apps.googleusercontent.com, but it is $googleWebClientId"
}

// The upload key never lives in the repository. A release build is signed only when all four
// variables are set, which only the release workflow does. Everywhere else it comes out unsigned.
val uploadKeyParts = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
val uploadKey: Map<String, String> = uploadKeyParts
    // A secret that is missing in a workflow arrives as an empty string, so blank counts as not set.
    .mapNotNull { part -> providers.environmentVariable("SPLITVIBE_UPLOAD_$part").orNull?.takeIf { it.isNotBlank() }?.let { part to it } }
    .toMap()
val hasUploadKey = uploadKey.size == uploadKeyParts.size
// Some but not all of them is a mistake in the release setup. Stopping here beats shipping an unsigned file.
check(uploadKey.isEmpty() || hasUploadKey) {
    "Release signing needs all four SPLITVIBE_UPLOAD_ variables or none. Missing: " +
        (uploadKeyParts - uploadKey.keys).joinToString { "SPLITVIBE_UPLOAD_$it" }
}

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
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
            buildConfigField("String", "AI_PROXY_URL", "\"$debugAiProxyUrl\"")
        }
        release {
            buildConfigField("String", "AI_PROXY_URL", "\"$releaseAiProxyUrl\"")
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
            // License and metadata files that several JVM libraries ship under the same names.
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
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.exifinterface)
    // Installs the baseline profile on devices where the store does not do it.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baseline-profile"))

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

    // Talks to the AI proxy; the answer streams in as lines of JSON.
    implementation(libs.okhttp)

    // Signing in with Google through the system's Credential Manager, which hands the app an ID
    // token that the proxy checks.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

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
    // Checks touch target sizes, contrast and labels on the screens the device tests visit.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}
