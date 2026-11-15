plugins {
    alias(libs.plugins.sunup.kmp.compose)
}

// The deck screens: My decks / Browse packs, New deck, Deck detail, game picker, card forms.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.decks.domain)
            implementation(projects.core.designSystem)
            // Browse packs: the map's country list, and the seeder that turns a region into a deck.
            implementation(projects.core.geo)
            implementation(projects.core.data)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
