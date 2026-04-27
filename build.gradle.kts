import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.test.logger)
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    pluginManager.withPlugin("java") {
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "checkstyle")
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "pmd")
        apply(plugin = "jacoco")

        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.toolchain {
            // Error Prone 2.49+ requires JDK 21+ to run the javac plugin; bytecode stays Java 17 via `release`.
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(17)

            val nullawaySeverity = if (this.name.contains("test", ignoreCase = true)) CheckSeverity.OFF else CheckSeverity.ERROR
            options.errorprone {
                check("NullAway", nullawaySeverity)
                check("VoidUsed", CheckSeverity.OFF)
                check("UnrecognisedJavadocTag", CheckSeverity.OFF)
                option("NullAway:AnnotatedPackages", "io.princeofspace")
            }
        }
        if (project.name.contains("core") || project.name.contains("spotless")) {
            logger.lifecycle("${project.name}: enabling javadocs & sources")
            javaExtension.withJavadocJar()
            javaExtension.withSourcesJar()
        }

        dependencies {
            add("errorprone", libs.errorprone.core.get())
            add("errorprone", libs.nullaway.get())
        }

        configure<CheckstyleExtension> {
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        }

        spotless {
            java {
                removeUnusedImports()
                importOrder("", "java|javax", "\\#")
            }
        }
    }

    // Publish all Maven artifacts to a single staging directory so the release workflow
    // can bundle them into a ZIP for Sonatype Central Portal upload.
    pluginManager.withPlugin("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "staging"
                    url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
                }
            }
        }
    }
}

dependencyAnalysis {
  issues {
    all {
      onAny {
        severity("fail")
      }
    }
  }
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "settings.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(2)
    }
    format("markdown") {
        target("**/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target(".gitattributes", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
