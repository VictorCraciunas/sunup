plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Setting up alarms: saving them to the app and the alarm engine together, keeping both in step.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            // AlarmService takes the engine's AlarmScheduler and AlarmStore, so whoever builds it needs them too.
            api(projects.core.alarm)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
