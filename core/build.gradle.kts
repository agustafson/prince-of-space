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
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.errorprone {
        option("NullAway:AnnotatedPackages", "io.princeofspace")
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
    useJUnitPlatform {
        includeTags("eval")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    environment("PRINCE_EVAL_ROOTS", System.getenv("PRINCE_EVAL_ROOTS") ?: "")
    environment("PRINCE_EVAL_REPORT_DIR", System.getenv("PRINCE_EVAL_REPORT_DIR") ?: "")
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
            from(components["java"])
            pom {
                name.set("prince-of-space-core")
                description.set("Deterministic Java source formatter (core library)")
                url.set("https://github.com/prince-of-space/prince-of-space")
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
                    connection.set("scm:git:git://github.com/prince-of-space/prince-of-space.git")
                    developerConnection.set("scm:git:ssh://git@github.com:prince-of-space/prince-of-space.git")
                    url.set("https://github.com/prince-of-space/prince-of-space")
                }
            }
        }
    }
}

signing {
    isRequired = false
    sign(publishing.publications["maven"])
}
