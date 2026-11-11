plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Shared domain models, repository interfaces, error types and Result. Depends on nothing else
// in the app; only on small pure-Kotlin libraries.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}
