plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

kotlin {
    // Align with CI `java-version: 17` matrix and project Java 17 release — JVM 17 cannot load
    // build logic compiled for JVM 21 (see Gradle #23993 / "Could not resolve project :buildSrc").
    jvmToolchain(17)
}
