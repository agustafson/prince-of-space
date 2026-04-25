plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.15.0"
}

base {
    archivesName.set("prince-of-space-intellij-plugin")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.6")
        bundledPlugin("com.intellij.java")
    }
    implementation(project(":core"))
    implementation(libs.javaparser.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            // Omit upper bound so the plugin installs on current and future IDEs.
            // provider { null } tells the Gradle plugin not to emit an until-build attribute,
            // which is the Marketplace-compliant way to leave the upper bound open.
            untilBuild = provider { null }
        }
    }
}

tasks.named("buildSearchableOptions") {
    enabled = false
}
