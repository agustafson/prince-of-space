plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

base {
    archivesName.set("prince-of-space-cli")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.picocli)
    runtimeOnly(libs.slf4j.simple)
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
