plugins {
    alias(libs.plugins.sunup.kmp.compose)
    // The flow's type-safe navigation routes.
    alias(libs.plugins.kotlinSerialization)
}

// The first-run screens: welcome, what to learn, rise time, promise, permissions, wake-up mode.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // AlarmService saves the first alarm; AlarmScheduler rings the test alarm.
            implementation(projects.core.alarm)
            implementation(projects.core.designSystem)
            // "World geography": the map's country list, the seeder, and the settings flag.
            implementation(projects.core.geo)
            implementation(projects.core.data)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // onboardingGraph(), added to App's NavHost.
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            // The permission checklist: system prompts and Settings screens.
            implementation(libs.androidx.activity.compose)
        }
    }
}
