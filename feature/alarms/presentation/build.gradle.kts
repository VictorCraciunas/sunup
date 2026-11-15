plugins {
    alias(libs.plugins.sunup.kmp.compose)
}

// The alarm list and the alarm editor.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.alarms.domain)
            implementation(projects.core.designSystem)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
