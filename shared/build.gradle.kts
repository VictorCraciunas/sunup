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

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
