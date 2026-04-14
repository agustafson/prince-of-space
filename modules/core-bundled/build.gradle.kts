plugins {
    `java-library`
    alias(libs.plugins.shadow)
    `maven-publish`
    signing
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.shadowJar)
    systemProperty(
        "bundled.jar.path",
        tasks.shadowJar.get().archiveFile.get().asFile.absolutePath,
    )
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("prince-of-space-bundled")
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.github.javaparser", "io.princeofspace.shaded.com.github.javaparser")
    relocate("org.slf4j", "io.princeofspace.shaded.org.slf4j")
    relocate("org.jspecify", "io.princeofspace.shaded.org.jspecify")
    manifest {
        attributes["Implementation-Title"] = "prince-of-space-bundled"
        attributes["Implementation-Version"] = project.version.toString()
    }
    // Ensure the shadow JAR is the main artifact consumers resolve
    dependsOn(project(":core").tasks.named("jar"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group.toString()
            artifactId = "prince-of-space-bundled"
            version = rootProject.version.toString()
            artifact(tasks.shadowJar)
            pom {
                name.set("prince-of-space-bundled")
                description.set("Shaded prince-of-space with no transitive dependencies")
                url.set("https://github.com/prince-of-space/prince-of-space")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                withXml {
                    val root = asNode()
                    val deps = root.get("dependencies")
                    if (deps != null) {
                        (deps as groovy.util.Node).children().clear()
                    }
                }
            }
        }
    }
}

signing {
    isRequired = false
    sign(publishing.publications["maven"])
}

