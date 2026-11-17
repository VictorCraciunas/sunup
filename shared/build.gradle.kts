plugins {
    alias(libs.plugins.sunup.kmp.compose)
    alias(libs.plugins.kotlinSerialization)
}

// The umbrella module: the app's root composable, plus the framework the iOS app links against.
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.designSystem)
            implementation(projects.feature.alarms.presentation)
            implementation(projects.feature.decks.presentation)
            implementation(projects.feature.onboarding.presentation)
            implementation(projects.feature.progress.presentation)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        iosMain.dependencies {
            // iOS start-up (IosApp.kt): Koin with the engine, database and settings, and the
            // morning flow shown over the app (Android shows it from its own activity).
            implementation(projects.core.alarm)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.feature.wakeup.presentation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}
