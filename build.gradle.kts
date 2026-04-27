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

val docsVenvDir = layout.projectDirectory.dir(".venv-docs")
val docsRequirementsFile = layout.projectDirectory.file("docs/requirements.txt")

val setupDocsToolchain by tasks.registering(Exec::class) {
    group = "documentation"
    description = "Create docs virtualenv and install MkDocs dependencies."
    inputs.file(docsRequirementsFile)
    outputs.file(docsVenvDir.file(".requirements-stamp"))
    commandLine(
        "sh",
        "-lc",
        """
        set -eu
        python3 -m venv .venv-docs
        ./.venv-docs/bin/python -m pip install --upgrade pip
        ./.venv-docs/bin/pip install -r docs/requirements.txt
        touch .venv-docs/.requirements-stamp
        """.trimIndent(),
    )
}

val generateCompareHtml by tasks.registering(Exec::class) {
    group = "documentation"
    description = "Regenerate examples/compare.html from committed outputs."
    dependsOn(setupDocsToolchain)
    inputs.file(layout.projectDirectory.file("scripts/generate-compare.py"))
    inputs.dir(layout.projectDirectory.dir("examples/outputs"))
    outputs.file(layout.projectDirectory.file("examples/compare.html"))
    commandLine("sh", "-lc", "set -eu; ./.venv-docs/bin/python scripts/generate-compare.py")
}

tasks.register<Exec>("docsSite") {
    group = "documentation"
    description = "Build docs site with strict MkDocs checks into _site."
    dependsOn(setupDocsToolchain)
    inputs.file(layout.projectDirectory.file("mkdocs.yml"))
    inputs.dir(layout.projectDirectory.dir("docs"))
    outputs.dir(layout.projectDirectory.dir("_site"))
    commandLine("sh", "-lc", "set -eu; ./.venv-docs/bin/python -m mkdocs build --strict --site-dir _site")
}
