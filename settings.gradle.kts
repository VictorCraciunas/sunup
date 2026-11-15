rootProject.name = "SunUp"

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
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":androidApp")
include(":shared")

include(":core:alarm")
include(":core:data")
include(":core:database")
include(":core:domain")
include(":core:geo")
include(":core:design-system")

include(":feature:alarms:domain")
include(":feature:alarms:presentation")

include(":feature:decks:domain")
include(":feature:decks:presentation")

include(":feature:wakeup:domain")
include(":feature:wakeup:presentation")
