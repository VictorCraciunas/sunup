plugins {
    alias(libs.plugins.sunup.kmp.library)
}

// The morning flow's rules: which cards stop the alarm, what goes in the bonus round.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
