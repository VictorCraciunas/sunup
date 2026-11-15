plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Repository implementations shared by several features, backed by :core:database,
// plus the small settings file (DataStore).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.database)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.datastore.preferences)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            // settingsStoreModule: the app's files folder.
            implementation(libs.koin.android)
        }
    }
}
