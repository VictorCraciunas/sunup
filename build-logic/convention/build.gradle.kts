import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "dev.victorc.sunup.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.sunup.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = libs.plugins.sunup.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = libs.plugins.sunup.kmp.compose.get().pluginId
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("room") {
            id = libs.plugins.sunup.room.get().pluginId
            implementationClass = "RoomConventionPlugin"
        }
    }
}
