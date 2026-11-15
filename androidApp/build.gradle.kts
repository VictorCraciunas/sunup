plugins {
    alias(libs.plugins.sunup.android.application)
}

android {
    namespace = "dev.victorc.sunup"

    defaultConfig {
        applicationId = "dev.victorc.sunup"
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.alarm)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.designSystem)
    implementation(projects.core.geo)
    implementation(projects.feature.alarms.presentation)
    implementation(projects.feature.wakeup.presentation)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
