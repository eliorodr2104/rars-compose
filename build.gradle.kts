import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "org.example"
version = "1.0-SNAPSHOT"

val githubProperties = Properties()
githubProperties.load(FileInputStream(rootProject.file("github.properties")))

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven {
        url = uri("https://maven.pkg.github.com/Qawaz/compose-code-editor")
        credentials {
            username = (githubProperties["gpr.usr"] ?: System.getenv("GPR_USER")).toString()
            password = (githubProperties["gpr.key"] ?: System.getenv("GPR_API_KEY")).toString()
        }
    }
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation("com.wakaztahir:codeeditor:3.0.5")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "rars-compose"
            packageVersion = "1.0.0"
        }
    }
}
