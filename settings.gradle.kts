pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Robolectric's Android image, 151 MB, comes from Google's copy of Maven Central and from
        // nowhere else: Maven Central kept answering GitHub's runners 429 Too Many Requests, or not
        // found, for it. Its checksum in gradle/verification-metadata.xml is the same either way.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2/") {
                    name = "GoogleMavenCentralMirror"
                }
            }
            filter {
                includeModule("org.robolectric", "android-all-instrumented")
            }
        }
    }
}

rootProject.name = "split-bill-poc"
include(":app")
include(":core:domain")
include(":core:designsystem")
include(":baseline-profile")
