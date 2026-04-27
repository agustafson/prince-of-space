plugins {
    `java-library`
    `maven-publish`
    signing
}

base {
    archivesName.set("prince-of-space-spotless")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.spotless.lib)

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
                minimum = BigDecimal(1)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // groupId/version from project; artifactId would default to "spotless".
            artifactId = "prince-of-space-spotless"
            from(components["java"])
            pom {
                name.set("prince-of-space-spotless")
                description.set("Spotless FormatterStep integration for prince-of-space")
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
