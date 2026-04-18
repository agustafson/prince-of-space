pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.autonomousapps.build-health") version "3.7.0"
    id("com.mooltiverse.oss.nyx") version "3.1.8-alpha.1"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "prince-of-space"

include(":core", ":core-bundled", ":spotless", ":cli", ":intellij-plugin")

project(":core").projectDir = file("modules/core")
project(":core-bundled").projectDir = file("modules/core-bundled")
project(":spotless").projectDir = file("modules/spotless")
project(":cli").projectDir = file("modules/cli")
project(":intellij-plugin").projectDir = file("modules/intellij-plugin")
