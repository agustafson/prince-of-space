import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.mkdocs.build)
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
        if (project.name in setOf("core", "core-bundled", "spotless")) {
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

// Declared in root settings via the Dependency Analysis Gradle Plugin; fails the build on declared-but-unused
// dependencies and similar issues across subprojects (see docs/contributing.md PR checks).
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
        targetExclude("_site/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target(".gitattributes", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

mkdocs {
    strict = true
    sourcesDir = "."
    buildDir = "_site"
    updateSiteUrl = false
    publish {
        docPath = ""
        rootRedirect = false
    }
}

python {
    requirements.file = "docs/requirements.txt"
    pip("mkdocs:1.6.1")
    pip("mkdocs-material:9.5.50")
}

tasks.register("regenerateExternalCompareOutputs") {
    group = "documentation"
    description = "Refresh examples/external/outputs via Spotless (GJF, Eclipse, Palantir, Prettier)."
    dependsOn(tasks.named(":external-compare:regenerateExternalCompareOutputs"))
}

val generateCompareHtml by tasks.registering(Exec::class) {
    group = "documentation"
    description = "Regenerate examples/compare.html from committed outputs."
    inputs.file(layout.projectDirectory.file("scripts/generate-compare.py"))
    inputs.dir(layout.projectDirectory.dir("examples/outputs"))
    inputs.dir(layout.projectDirectory.dir("examples/external/outputs"))
    outputs.file(layout.projectDirectory.file("examples/compare.html"))
    commandLine("sh", "-lc", "set -eu; python3 scripts/generate-compare.py")
}

val docsSite = tasks.register<Exec>("docsSite") {
    group = "documentation"
    description = "Build docs site with strict MkDocs checks into _site."
    dependsOn("pipInstall", generateCompareHtml)
    inputs.file(layout.projectDirectory.file("mkdocs.yml"))
    inputs.dir(layout.projectDirectory.dir("docs"))
    outputs.dir(layout.projectDirectory.dir("_site"))
    commandLine("sh", "-lc", "set -eu; ./.gradle/python/bin/python -m mkdocs build --strict --site-dir _site -f mkdocs.yml")
}
tasks.register("generateDocs") {
    group = "documentation"
    description = "Generate docs."
    dependsOn(generateCompareHtml, docsSite)
}
tasks.register("assembleWithDocs") {
    group = "build"
    description =
        "Runs all assemble tasks plus generateDocs (MkDocs + Python). Plain `./gradlew assemble` stays JVM-only."
    dependsOn(tasks.assemble, tasks.named("generateDocs"))
}
