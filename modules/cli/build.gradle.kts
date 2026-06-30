plugins {
    `java-library`
    alias(libs.plugins.shadow)
    `maven-publish`
    signing
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

tasks.jar {
    // Plain jar isn't published; keep its output filename off both the shadowJar's path and the
    // "prince-of-space-cli-*.jar" glob the VS Code extension scans for (see formatter.ts), so the
    // two jar tasks never race to write the same file and the IDE never picks up the unshaded jar.
    // (Gradle flags the path collision as an implicit-dependency validation problem since it can
    // otherwise publish/sign the wrong jar depending on task order.)
    archiveBaseName.set("cli")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            // groupId/version from project; artifactId would default to "cli".
            artifactId = "prince-of-space-cli"
            artifact(tasks.shadowJar)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
            pom {
                name.set("prince-of-space-cli")
                description.set("Self-contained CLI for prince-of-space; runnable via `java -jar` and " +
                    "intended for invocation from external tools (e.g. Maven's Spotless nativeCmd step)")
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
                // Use XmlProvider.asElement() (W3C DOM), not Groovy Node — avoids Node/NodeList quirks.
                withXml {
                    val projectEl = asElement()
                    val dependencyBlocks = projectEl.getElementsByTagName("dependencies")
                    for (i in 0 until dependencyBlocks.length) {
                        val block = dependencyBlocks.item(i)
                        if (block is org.w3c.dom.Element) {
                            while (block.hasChildNodes()) {
                                block.removeChild(block.firstChild)
                            }
                        }
                    }
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
