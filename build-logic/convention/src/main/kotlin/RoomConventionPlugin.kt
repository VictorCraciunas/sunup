import androidx.room.gradle.RoomExtension
import dev.victorc.sunup.buildlogic.libs
import dev.victorc.sunup.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Room KMP with the bundled SQLite driver. Apply after `sunup.kmp.library`. */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            // Exported schemas are committed, so migrations can be checked later.
            schemaDirectory("$projectDir/schemas")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(libs.library("androidx-room-runtime"))
                implementation(libs.library("androidx-sqlite-bundled"))
            }
        }

        dependencies {
            // One KSP run per target.
            listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { configuration ->
                add(configuration, libs.library("androidx-room-compiler"))
            }
        }
    }
}
