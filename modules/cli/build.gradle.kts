plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

base {
    archivesName.set("prince-of-space-cli")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.javaparser.core)
    implementation(libs.picocli)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
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
                minimum = BigDecimal(0.55)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("prince-of-space-cli")
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "io.princeofspace.cli.Main"
        attributes["Implementation-Title"] = "prince-of-space-cli"
        attributes["Implementation-Version"] = project.version.toString()
    }
}
