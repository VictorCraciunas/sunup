plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Making decks and cards: the card forms, checking them, and saving.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
