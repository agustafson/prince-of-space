plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.14.0"
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
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            // Open upper bound so the plugin installs on current and future IDEs (e.g. 261+); verify APIs when upgrading the platform dependency.
            untilBuild = "999.*"
        }
    }
}

tasks.named("buildSearchableOptions") {
    enabled = false
}
