import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.ksp)
}

// Where the app reaches the AI proxy (see proxy/ and docs/proxy.md), set as splitvibe.aiProxyUrl in
// local.properties (git-ignored). Debug and release builds use the same deployed proxy. No key of
// any kind is built into the app, and without the setting it is built with AI reading off.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val aiProxyUrl: String = localProperties.getProperty("splitvibe.aiProxyUrl")?.trim()?.trimEnd('/').orEmpty()
// Something that is not an https address, such as one with <your-subdomain> still in it, would only
// turn AI reading off once the app runs, so it stops every build.
if (aiProxyUrl.isNotEmpty()) {
    val address = runCatching { URI(aiProxyUrl) }.getOrNull()
    check(address != null && address.scheme == "https" && !address.host.isNullOrEmpty()) {
        "splitvibe.aiProxyUrl must be an https address such as https://split-vibe-proxy.example.workers.dev, but it is $aiProxyUrl"
    }
}
// The app's web client ID at Google (public): Google's ID tokens for the app are made out to it, and
// the proxy checks that. Without it the app is built with signing in turned off.
val googleWebClientId: String = localProperties.getProperty("splitvibe.googleWebClientId")?.trim().orEmpty()
check(googleWebClientId.isEmpty() || Regex("[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com").matches(googleWebClientId)) {
    "splitvibe.googleWebClientId must be a web client ID such as 1234-abcd.apps.googleusercontent.com, but it is $googleWebClientId"
}

// RevenueCat's public SDK key for the app on Google Play, which is made to ship inside the app. Pro
// is sold only in a build that has it; without it the Pro screen says Pro is not available, and
// every other flow works the same. Only the prod flavor gets it (see productFlavors). Anything else,
// such as RevenueCat's secret key, stops the build, and the message leaves the value out.
val revenueCatApiKey: String = localProperties.getProperty("splitvibe.revenueCatApiKey")?.trim().orEmpty()
check(revenueCatApiKey.isEmpty() || Regex("goog_[A-Za-z0-9_-]+").matches(revenueCatApiKey)) {
    "splitvibe.revenueCatApiKey must be RevenueCat's public SDK key for Google Play, which starts with goog_"
}

// Firebase is used for one thing only, receiving the push notices the proxy sends about requests and
// groups. Its four values are public settings of the Android app in the Firebase console (Project
// settings), set in local.properties. Without all four the app is built without push, and every
// other flow works the same.
val firebaseSettings = mapOf(
    "APP_ID" to "splitvibe.firebaseAppId",
    "API_KEY" to "splitvibe.firebaseApiKey",
    "PROJECT_ID" to "splitvibe.firebaseProjectId",
    "SENDER_ID" to "splitvibe.firebaseSenderId",
)
val firebase: Map<String, String> = firebaseSettings.mapNotNull { (field, property) ->
    localProperties.getProperty(property)?.trim()?.takeIf { it.isNotEmpty() }?.let { field to it }
}.toMap()
check(firebase.isEmpty() || firebase.size == firebaseSettings.size) {
    "Push needs all four Firebase settings or none. Missing: " +
        (firebaseSettings.keys - firebase.keys).joinToString { firebaseSettings.getValue(it) }
}

// The release workflow sets the version from the tag it builds, with -Psplitvibe.versionCode and
// -Psplitvibe.versionName. Without them a build is version 1, "0.1.0". A blank value counts as not set;
// a code that is not a whole number Google Play takes stops the build.
val appVersionCode: Int = providers.gradleProperty("splitvibe.versionCode").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
    code.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
        ?: throw GradleException("splitvibe.versionCode must be a whole number from 1 to 2100000000, but it is $code")
} ?: 1
val appVersionName: String = providers.gradleProperty("splitvibe.versionName").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "0.1.0"

// Invite and payment links point at the proxy's own address, which opens them in the app once
// Android has checked the proxy's assetlinks.json.
fun linkHostOf(url: String): String = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "links.invalid"

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

// The Android image that Robolectric runs the local tests on, a dependency like any other: Gradle
// downloads it (from where settings.gradle.kts says), checks it against
// gradle/verification-metadata.xml, and CI keeps it in its Gradle cache. robolectricImages puts it
// in the folder the tests read it from.
val robolectricAndroid: Configuration = configurations.create("robolectricAndroid") {
    isCanBeConsumed = false
}
val robolectricImages = tasks.register<Sync>("robolectricImages") {
    from(robolectricAndroid)
    into(layout.buildDirectory.dir("robolectric"))
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
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
        firebaseSettings.keys.forEach { field -> buildConfigField("String", "FIREBASE_$field", "\"${firebase[field].orEmpty()}\"") }
        buildConfigField("String", "AI_PROXY_URL", "\"$aiProxyUrl\"")
        manifestPlaceholders["linkHost"] = linkHostOf(aiProxyUrl)
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
            // The pseudolocales en-XA (longer text with accents) and ar-XB (right to left), to find text
            // that is cut off, overlaps or is not in the resources. Release builds leave them out.
            isPseudoLocalesEnabled = true
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
            // Google Play sells Pro only to the prod package, so a dev build never calls RevenueCat:
            // without the key it says that Pro is not available in this build.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
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
            // Robolectric takes its Android image from robolectricImages and downloads nothing. Its own
            // download, without a retry or a cache, failed whole CI runs on Maven Central's 429.
            test.inputs.files(robolectricImages).withPropertyName("robolectricImages").withPathSensitivity(PathSensitivity.NAME_ONLY)
            test.systemProperty("robolectric.offline", "true")
            test.systemProperty("robolectric.dependency.dir", robolectricImages.get().destinationDir.path)
            // Amounts and dates follow the phone's region. Robolectric sets it from a test's qualifiers,
            // the United States without them; a plain JUnit test has the machine's, set here to the same.
            test.systemProperty("user.language", "en")
            test.systemProperty("user.country", "US")
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

    lint {
        // The app's lint also checks the modules it depends on, in its own report: :core:designsystem,
        // and :core:domain through the lint plugin that module applies. Lint runs its check of API
        // levels (NewApi) only on Android modules, so it does not reach :core:domain, a plain Kotlin
        // library: lint's ApiDetector skips a module of type JAVA_LIBRARY.
        checkDependencies = true
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

// Room writes the shape of each database version here, so a later version can be migrated to and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    // The document scanner of Google Play services: finds the bill's edges, straightens it, takes several pages.
    implementation(libs.mlkit.document.scanner)

    // Bills, drafts and the people the user splits with live in a database on the phone.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Makes the drafts of recurring bills on their days, also while the app is closed.
    implementation(libs.androidx.work.runtime.ktx)

    // Talks to the AI proxy; the answer streams in as lines of JSON.
    implementation(libs.okhttp)

    // Draws the payment QR codes a request carries.
    implementation(libs.zxing.core)
    // Receives the proxy's push notices about requests and groups; Firebase is used for nothing else.
    implementation(libs.firebase.messaging)

    // Signing in with Google through the system's Credential Manager, which hands the app an ID
    // token that the proxy checks.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Sells Pro through Google Play: RevenueCat checks and keeps the purchases, and brings Play
    // Billing Library 9.1.0 with it. The proxy, not the app, decides what Pro gives.
    implementation(libs.revenuecat.purchases)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    robolectricAndroid(libs.robolectric.android)
    testImplementation(libs.roborazzi)
    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.work.testing)
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
