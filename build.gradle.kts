plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.test.logger)
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    pluginManager.withPlugin("java") {
        apply(plugin = "com.diffplug.spotless")
        apply(plugin = "pmd")
        apply(plugin = "jacoco")

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
