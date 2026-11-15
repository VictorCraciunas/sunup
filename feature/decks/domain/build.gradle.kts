plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// Making decks and cards: the card forms, checking them, and importing decks from JSON.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
            // Import: reading the deck JSON an AI writes.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
