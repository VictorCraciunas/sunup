plugins {
    alias(libs.plugins.sunup.kmp.library)
    alias(libs.plugins.kotlinSerialization)
}

// The alarm engine: when alarms ring, the native scheduling per platform, and the ring log.
// Rule #1 lives here: nothing on the ring path may depend on the network or credential-encrypted storage.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // AlarmScheduler returns core:domain's Result, so callers need it too.
            api(projects.core.domain)

            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.koin.android)
        }
    }
}
