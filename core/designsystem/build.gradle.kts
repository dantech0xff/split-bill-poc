plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dantech.splitvibe.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { test ->
            // WebTokensTest checks web/src/tokens.css against the tokens; -PwebTokens=write writes it again.
            test.systemProperty("sv.webTokens", providers.gradleProperty("webTokens").getOrElse("check"))
        }
    }
}

dependencies {
    // Screens get Compose and Material through this module, so its public types match theirs.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    // The icons the components draw themselves.
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit)
}
