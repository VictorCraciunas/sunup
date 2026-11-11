plugins {
    alias(libs.plugins.sunup.kmp.compose)
}

// The morning flow on screen: silencing card → bonus round → "You're up!".
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.wakeup.domain)
            implementation(projects.core.designSystem)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
