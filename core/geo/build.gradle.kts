plugins {
    alias(libs.plugins.sunup.kmp.compose)
    alias(libs.plugins.kotlinSerialization)
}

// Country outlines (from Natural Earth, public domain) and the interactive map the geography
// game is played on. The map file is built by tools/geo/build_countries.py.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.designSystem)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    packageOfResClass = "dev.victorc.sunup.core.geo.resources"
    publicResClass = false
}
