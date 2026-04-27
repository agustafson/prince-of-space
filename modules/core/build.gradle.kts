plugins {
    `java-library`
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.test.logger)
    `maven-publish`
    signing
}

base {
    archivesName.set("prince-of-space-core")
}

dependencies {
    api(libs.jspecify)
    api(libs.slf4j.api)
    implementation(libs.javaparser.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.jqwik)
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
}

tasks.test {
    useJUnitPlatform {
        excludeTags("eval")
    }
    finalizedBy(tasks.jacocoTestReport)
}

val evalTest by tasks.registering(Test::class) {
    description = "Runs the real-world evaluation harness (requires PRINCE_EVAL_ROOTS to be set)."
    group = "verification"
    // Reads PRINCE_EVAL_* from the invoking environment; must not be frozen by configuration cache.
    notCompatibleWithConfigurationCache("eval harness uses environment variables")
    useJUnitPlatform {
        includeTags("eval")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    environment("PRINCE_EVAL_ROOTS", System.getenv("PRINCE_EVAL_ROOTS") ?: "")
    environment("PRINCE_EVAL_REPORT_DIR", System.getenv("PRINCE_EVAL_REPORT_DIR") ?: "")
    environment("PRINCE_EVAL_REPORT_SLUG", System.getenv("PRINCE_EVAL_REPORT_SLUG") ?: "")
    environment("PRINCE_EVAL_LINE_LENGTH", System.getenv("PRINCE_EVAL_LINE_LENGTH") ?: "")
    environment("PRINCE_EVAL_WRAP_STYLE", System.getenv("PRINCE_EVAL_WRAP_STYLE") ?: "")
    environment("PRINCE_EVAL_MAX_OVER_LONG_SAMPLES", System.getenv("PRINCE_EVAL_MAX_OVER_LONG_SAMPLES") ?: "")
    environment("MAX_OVER_LONG_LINE_SAMPLES", System.getenv("MAX_OVER_LONG_LINE_SAMPLES") ?: "")
    environment("PRINCE_EVAL_SKIP_SECOND_FORMAT", System.getenv("PRINCE_EVAL_SKIP_SECOND_FORMAT") ?: "")
    // Heap for the forked test worker only (Gradle daemon is separate). Override with PRINCE_EVAL_MAX_HEAP
    // if the default is too small for the corpus; PRINCE_EVAL_SKIP_SECOND_FORMAT=true also reduces use.
    maxHeapSize = System.getenv("PRINCE_EVAL_MAX_HEAP")?.takeIf { it.isNotBlank() } ?: "1g"
    jvmArgs("-XX:+UseG1GC")
    extensions.configure<JacocoTaskExtension> {
        isEnabled = false
    }
    dependsOn(tasks.testClasses)
}

tasks.register<Test>("showroomGoldenTest") {
    group = "verification"
    description =
        "Assert Formatter output matches authoritative examples/outputs/**/FormatterShowcase goldens (java8/java17/java21/java25)"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("showroom-golden")
    }
    dependsOn(tasks.testClasses)
}

tasks.register<JavaExec>("writeGolden") {
    group = "verification"
    description = "Regenerate matrix/sample/Expected.wide_balanced.java from matrix/sample/Before.java"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.princeofspace.WriteGolden")
}

tasks.register<JavaExec>("generateMatrixGoldens") {
    group = "verification"
    description = "Regenerate src/test/resources/matrix/<use-case>/ golden files"
    // Use main runtime + compiled test classes only (not test runtimeClasspath) so this task does not depend on
    // processTestResources; then processTestResources can run after goldens are written in the same build.
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].output.classesDirs
    mainClass.set("io.princeofspace.GenerateMatrixGoldens")
    workingDir = projectDir
    dependsOn(tasks.compileJava, tasks.compileTestJava)
}

tasks.processTestResources {
    mustRunAfter(tasks.named("generateMatrixGoldens"))
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal(0.85)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    ignoreFailures.set(false)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // groupId/version come from project (root subprojects convention). Only artifactId
            // defaults to project.name ("core"); override so it matches archivesName / README.
            artifactId = "prince-of-space-core"
            from(components["java"])
            pom {
                name.set("prince-of-space-core")
                description.set("Deterministic Java source formatter (core library)")
                url.set("https://github.com/agustafson/prince-of-space")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("prince-of-space")
                        name.set("prince-of-space contributors")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/agustafson/prince-of-space.git")
                    developerConnection.set("scm:git:ssh://git@github.com:prince-of-space/prince-of-space.git")
                    url.set("https://github.com/agustafson/prince-of-space")
                }
            }
        }
    }
}

signing {
    val key = providers.environmentVariable("GPG_PRIVATE_KEY").orNull
    val pass = providers.environmentVariable("GPG_PASSPHRASE").orNull
    if (!key.isNullOrBlank() && !pass.isNullOrBlank()) {
        useInMemoryPgpKeys(key, pass)
    }
    isRequired = !key.isNullOrBlank()
    sign(publishing.publications["maven"])
}
