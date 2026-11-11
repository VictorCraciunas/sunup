import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.victorc.sunup.buildlogic.libs
import dev.victorc.sunup.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** A Kotlin Multiplatform library with Compose Multiplatform UI. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("sunup.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            // Compose resources (strings, fonts, images) need Android resources turned on.
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources.enable = true
            }

            sourceSets.commonMain.dependencies {
                implementation(libs.library("compose-runtime"))
                implementation(libs.library("compose-foundation"))
                implementation(libs.library("compose-ui"))
                implementation(libs.library("compose-material3"))
                implementation(libs.library("compose-components-resources"))
                implementation(libs.library("compose-uiToolingPreview"))
            }
        }

        dependencies {
            // Lets Android Studio render @Preview for this module.
            add("androidRuntimeClasspath", libs.library("compose-uiTooling"))
        }
    }
}
