plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Repository implementations shared by several features, backed by :core:database,
// the small settings file (DataStore), and "You're up!"'s weather and calendar.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.database)

            implementation(libs.kotlinx.coroutines.core)
            // Reading MET Norway's forecast.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.datastore.preferences)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            // settingsStoreModule and morningInfoModule: the app's files folder and Context.
            implementation(libs.koin.android)
        }
    }
}
