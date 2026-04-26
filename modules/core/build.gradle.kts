import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    checkstyle
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.test.logger)
    `maven-publish`
    signing
}

base {
    archivesName.set("prince-of-space-core")
}

java {
    toolchain {
        // Error Prone 2.49+ requires JDK 21+ to run the javac plugin; bytecode stays Java 17 via `release`.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(libs.jspecify)
    api(libs.slf4j.api)
    implementation(libs.javaparser.core)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "io.princeofspace")
        check("NullAway", CheckSeverity.ERROR)
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        // NullAway is enforced on main sources only; test sources stay unchecked (see compileJava).
        check("NullAway", CheckSeverity.OFF)
    }
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
    // Reads PRINCE_EVAL_* from the invoking environment; must not be frozen by configuration cache
    // (otherwise PRINCE_EVAL_CONFIG_NAMES from an earlier run can stick and subset configs silently).
    notCompatibleWithConfigurationCache("eval harness uses environment variables")
    useJUnitPlatform {
        includeTags("eval")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    environment("PRINCE_EVAL_ROOTS", System.getenv("PRINCE_EVAL_ROOTS") ?: "")
    environment("PRINCE_EVAL_REPORT_DIR", System.getenv("PRINCE_EVAL_REPORT_DIR") ?: "")
    environment("PRINCE_EVAL_CONFIG_NAMES", System.getenv("PRINCE_EVAL_CONFIG_NAMES") ?: "")
    environment("PRINCE_EVAL_MAX_OVER_LONG_SAMPLES", System.getenv("PRINCE_EVAL_MAX_OVER_LONG_SAMPLES") ?: "")
    environment("MAX_OVER_LONG_LINE_SAMPLES", System.getenv("MAX_OVER_LONG_LINE_SAMPLES") ?: "")
    environment("PRINCE_EVAL_SKIP_SECOND_FORMAT", System.getenv("PRINCE_EVAL_SKIP_SECOND_FORMAT") ?: "")
    // Heap for the forked test worker only (Gradle daemon is separate). On ~8 GiB hosts prefer 5g–6g
    // and set PRINCE_EVAL_SKIP_SECOND_FORMAT=true if needed; override with PRINCE_EVAL_MAX_HEAP.
    maxHeapSize = System.getenv("PRINCE_EVAL_MAX_HEAP")?.takeIf { it.isNotBlank() } ?: "5g"
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

checkstyle {
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
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
