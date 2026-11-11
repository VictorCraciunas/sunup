import com.android.build.api.dsl.ApplicationExtension
import dev.victorc.sunup.buildlogic.JAVA_VERSION
import dev.victorc.sunup.buildlogic.JVM_TARGET
import dev.victorc.sunup.buildlogic.libs
import dev.victorc.sunup.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** The Android app module (`:androidApp`). Kotlin is built into AGP 9, so no Kotlin plugin is applied. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.versionInt("android-compileSdk")
            defaultConfig {
                minSdk = libs.versionInt("android-minSdk")
                targetSdk = libs.versionInt("android-targetSdk")
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            buildFeatures {
                compose = true
            }
        }

        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions.jvmTarget.set(JVM_TARGET)
        }
    }
}
