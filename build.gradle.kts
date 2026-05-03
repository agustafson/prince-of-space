import java.io.File
import java.nio.charset.StandardCharsets

import io.princeofspace.gradle.SpringBenchmarkReadmeInsert

import net.ltgt.gradle.errorprone.CheckSeverity
import org.gradle.api.tasks.testing.Test
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

        tasks.withType<Test>().configureEach {
            maxParallelForks =
                providers.gradleProperty("test.maxParallelForks")
                    .map(String::toInt)
                    .orElse(1)
                    .get()
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

tasks.register("syncReadmeVersions") {
    group = "documentation"
    description =
        "Rewrite README Maven/Gradle coordinates and the Quick Start version line to match rootProject.version."
    val versionString = version.toString()
    val readmeFile = File(rootProject.rootDir, "README.md")
    onlyIf {
        val ci = System.getenv("CI") == "true"
        val allowInCi = System.getenv("RUN_README_SYNC") == "true"
        !ci || allowInCi
    }
    doLast {
        val v = versionString
        var text = readmeFile.readText(StandardCharsets.UTF_8)
        val coord =
            Regex("(io\\.github\\.agustafson\\.princeofspace:prince-of-space-[a-z-]+:)([^\\s`\"]+)")
        text = coord.replace(text) { mr -> "${mr.groupValues[1]}$v" }
        val mavenCore =
            Regex(
                "(<groupId>io\\.github\\.agustafson\\.princeofspace</groupId>\\s*<artifactId>prince-of-space-core</artifactId>\\s*<version>)([^<]+)(</version>)",
                setOf(RegexOption.MULTILINE),
            )
        text = mavenCore.replace(text) { mr -> "${mr.groupValues[1]}$v${mr.groupValues[3]}" }
        val quickStart =
            Regex("(gradle\\.properties\\]\\(gradle\\.properties\\):\\*\\* )`[^`]+`")
        text = quickStart.replace(text) { mr -> "${mr.groupValues[1]}`$v`" }
        readmeFile.writeText(text, StandardCharsets.UTF_8)
    }
}

tasks.register("syncReadmePerfSection") {
    group = "documentation"
    description =
        "Replace the <!-- sync:perf:start --> … <!-- sync:perf:end --> block in README.md using docs/perf-results/spring-framework.md."
    val readmeFile = File(rootProject.rootDir, "README.md")
    val perfReportFile = File(rootProject.rootDir, "docs/perf-results/spring-framework.md")
    inputs.file(perfReportFile)
    outputs.file(readmeFile)
    onlyIf {
        val ci = System.getenv("CI") == "true"
        val allowInCi = System.getenv("RUN_README_SYNC") == "true"
        !ci || allowInCi
    }
    doLast {
        val reportFile = perfReportFile
        require(reportFile.exists()) {
            "Missing ${reportFile.path}; run :formatter-benchmark:run with PRINCE_BENCH_ROOT set first."
        }
        val readme = readmeFile.readText(StandardCharsets.UTF_8)
        val startMarker = "<!-- sync:perf:start -->"
        val endMarker = "<!-- sync:perf:end -->"
        val startIdx = readme.indexOf(startMarker)
        val endIdx = readme.indexOf(endMarker)
        require(startIdx >= 0 && endIdx > startIdx) {
            "README.md must contain '$startMarker' and '$endMarker' around the Spring benchmark section."
        }

        val report = reportFile.readText(StandardCharsets.UTF_8)
        val generated = SpringBenchmarkReadmeInsert.fromReport(report)

        val before = readme.substring(0, startIdx + startMarker.length)
        val after = readme.substring(endIdx)
        readmeFile.writeText("$before\n\n$generated\n\n$after", StandardCharsets.UTF_8)
    }
}

tasks.register("refreshSpringBenchmarkReadme") {
    group = "documentation"
    description =
        "Run :formatter-benchmark:run (needs PRINCE_BENCH_ROOT), then sync README perf block and coordinates."
}

tasks.named("assemble") {
    dependsOn(tasks.named("syncReadmeVersions"))
}

afterEvaluate {
    tasks.named("syncReadmePerfSection").configure {
        mustRunAfter(project(":formatter-benchmark").tasks.named("run"))
    }
    tasks.named("syncReadmeVersions").configure {
        mustRunAfter(tasks.named("syncReadmePerfSection"))
    }
    tasks.named("refreshSpringBenchmarkReadme").configure {
        dependsOn(
            project(":formatter-benchmark").tasks.named("run"),
            tasks.named("syncReadmePerfSection"),
            tasks.named("syncReadmeVersions"),
        )
    }
    tasks.named("spotlessMarkdown").configure {
        mustRunAfter(tasks.named("syncReadmePerfSection"))
        mustRunAfter(tasks.named("syncReadmeVersions"))
    }
}
