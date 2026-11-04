rootProject.name = "EventMap"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.chromium")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://artifactory.2gis.dev/sdk-maven-release")
            content { includeGroup("ru.dgis.sdk") }
        }
        // 2GIS builds do not need Mapbox download credentials.
        if (providers.gradleProperty("eventmap.mapProvider").orElse("mapbox").get() == "mapbox") maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull ?: ""
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":androidApp")
include(":common:core:platform")
include(":common:core:datastore")
include(":common:core:network")
include(":common:core:ui")
include(":common:core:utils")
include(":common:core:files")
include(":common:core:biometry")
include(":common:core:logger")
include(":common:core:connectivity")
include(":common:feature:example:domain")
include(":common:feature:example:data")
include(":common:feature:example:ui")
include(":common:feature:map:ui")
include(":common:feature:auth:domain")
include(":common:feature:auth:data")
include(":common:feature:auth:ui")
include(":common:feature:event:domain")
include(":common:feature:event:data")
include(":common:feature:event:ui")
include(":common:feature:profile:domain")
include(":common:feature:profile:data")
include(":common:feature:profile:ui")
include(":common:feature:feed:domain")
include(":common:feature:feed:data")
include(":common:feature:feed:ui")
include(":common:feature:search:domain")
include(":common:feature:search:data")
include(":common:feature:search:ui")
include(":common:feature:video:domain")
include(":common:feature:video:data")
include(":common:feature:video:ui")
include(":common:feature:business:ui")
include(":common:feature:quest:ui")
include(":common:feature:payment:ui")
include(":common:feature:game:ui")
include(":common:feature:invite:ui")
include(":common:feature:chat:ui")
include(":common:feature:moderation:ui")
include(":common:feature:settings:ui")
