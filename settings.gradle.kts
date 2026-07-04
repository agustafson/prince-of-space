pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.autonomousapps.build-health") version "3.16.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "prince-of-space"

include(":core", ":core-bundled", ":spotless", ":cli", ":intellij-plugin", ":external-compare", ":formatter-benchmark")

project(":core").projectDir = file("modules/core")
project(":core-bundled").projectDir = file("modules/core-bundled")
project(":spotless").projectDir = file("modules/spotless")
project(":cli").projectDir = file("modules/cli")
project(":intellij-plugin").projectDir = file("modules/intellij-plugin")
project(":external-compare").projectDir = file("modules/external-compare")
project(":formatter-benchmark").projectDir = file("modules/formatter-benchmark")
