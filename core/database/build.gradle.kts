plugins {
    alias(libs.plugins.sunup.kmp.library)
    alias(libs.plugins.sunup.room)
    alias(libs.plugins.kotlinSerialization)
}

// The app's Room database: decks, notes, review state and log, alarm settings.
// Lives in credential-encrypted storage, so nothing on the alarm's ring path may touch it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
