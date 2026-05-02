import com.diffplug.gradle.spotless.JavaExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.getByType

plugins {
    alias(libs.plugins.spotless)
}

// Resolve npm package versions by key (avoids kotlin-dsl / Spotless name collisions on "prettier").
val npmPrettierVersion: String =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("npm-prettier").get().requiredVersion
val npmPrettierPluginJavaVersion: String =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("npm-prettier-plugin-java").get().requiredVersion

val externalWorkDir = layout.buildDirectory.dir("external-compare-work")
val javaLevels = listOf("java8", "java17", "java21", "java25")
val outputFormatters = listOf("google-java-format", "eclipse", "palantir-aosp", "prettier")

val prepareExternalCompareWork by tasks.registering(Sync::class) {
    description = "Stage FormatterShowcase inputs per Java level for each external Spotless formatter."
    group = "documentation"
    into(externalWorkDir)
    outputFormatters.forEach { fmt ->
        javaLevels.forEach { level ->
            from(rootProject.layout.projectDirectory.file("examples/inputs/$level/FormatterShowcase.java")) {
                into("$fmt/$level")
            }
        }
    }
}

spotless {
    setEnforceCheck(false)

    format("compareGoogleJavaFormat", JavaExtension::class.java) {
        target(
            fileTree(layout.buildDirectory.dir("external-compare-work/google-java-format")) {
                include("**/*.java")
            },
        )
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
    format("compareEclipse", JavaExtension::class.java) {
        target(
            fileTree(layout.buildDirectory.dir("external-compare-work/eclipse")) {
                include("**/*.java")
            },
        )
        eclipse(libs.versions.eclipse.jdt.get())
    }
    format("comparePalantirAosp", JavaExtension::class.java) {
        target(
            fileTree(layout.buildDirectory.dir("external-compare-work/palantir-aosp")) {
                include("**/*.java")
            },
        )
        palantirJavaFormat(libs.versions.palantir.java.format.get()).style("AOSP")
    }
    format("comparePrettier", JavaExtension::class.java) {
        target(
            fileTree(layout.buildDirectory.dir("external-compare-work/prettier")) {
                include("**/*.java")
            },
        )
        val npmPackages: MutableMap<String, String> =
            mutableMapOf(
                "prettier" to npmPrettierVersion,
                "prettier-plugin-java" to npmPrettierPluginJavaVersion,
            )
        prettier(npmPackages).config(
            mapOf<String, Any>(
                "parser" to "java",
                "tabWidth" to 4,
                "plugins" to listOf("prettier-plugin-java"),
            ),
        )
    }
}

afterEvaluate {
    tasks.configureEach {
        if (name.startsWith("spotless")) {
            dependsOn(prepareExternalCompareWork)
        }
    }
}

val copyExternalCompareOutputs by tasks.registering(Exec::class) {
    description = "Copy formatted worktrees into examples/external/outputs for compare.html."
    group = "documentation"
    dependsOn("spotlessApply")
    val workRoot =
        layout.buildDirectory.get().asFile.resolve("external-compare-work").absolutePath
    val outRoot = rootProject.layout.projectDirectory.asFile.resolve("examples/external/outputs").absolutePath
    environment("COMPARE_WORK", workRoot)
    environment("COMPARE_OUT", outRoot)
    commandLine(
        "sh",
        "-c",
        """
        set -euo pipefail
        for fmt in google-java-format eclipse palantir-aosp prettier; do
          for lev in java8 java17 java21 java25; do
            install -d "${'$'}COMPARE_OUT/${'$'}fmt"
            cp "${'$'}COMPARE_WORK/${'$'}fmt/${'$'}lev/FormatterShowcase.java" \
              "${'$'}COMPARE_OUT/${'$'}fmt/${'$'}lev.java"
          done
        done
        """.trimIndent(),
    )
}

tasks.register("regenerateExternalCompareOutputs") {
    group = "documentation"
    description =
        "Run Spotless (Google Java Format, Eclipse JDT, Palantir AOSP, Prettier) on showroom inputs and refresh examples/external/outputs."
    dependsOn(copyExternalCompareOutputs)
}
