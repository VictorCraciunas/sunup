plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Repository implementations shared by several features, backed by :core:database.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.database)

            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}
