import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.victorc.sunup.buildlogic.JVM_TARGET
import dev.victorc.sunup.buildlogic.libs
import dev.victorc.sunup.buildlogic.library
import dev.victorc.sunup.buildlogic.pathNamespace
import dev.victorc.sunup.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A Kotlin Multiplatform library for Android and iOS. Every shared module uses this,
 * directly or through `sunup.kmp.compose`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()

            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = pathNamespace
                compileSdk = libs.versionInt("android-compileSdk")
                minSdk = libs.versionInt("android-minSdk")
                compilerOptions {
                    jvmTarget.set(JVM_TARGET)
                }
                withHostTest {}
            }

            sourceSets.commonTest.dependencies {
                implementation(libs.library("kotlin-test"))
                implementation(libs.library("kotlinx-coroutines-test"))
                implementation(libs.library("turbine"))
                implementation(libs.library("assertk"))
            }
        }
    }
}
