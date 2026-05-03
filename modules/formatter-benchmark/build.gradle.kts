plugins {
    application
    java
}

base {
    archivesName.set("formatter-benchmark")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.google.java.format)
    implementation(libs.palantir.java.format)
}

application {
    mainClass.set("io.princeofspace.benchmark.FormatterBenchmark")
}

// google-java-format / Palantir Java Format use javac internals; JDK 16+ requires explicit exports.
val formatterJvmOpens =
    listOf(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )

tasks.named<JavaExec>("run") {
    jvmArgs(formatterJvmOpens)
    systemProperty("PRINCE_BENCH_FORMATTER_VERSION", project.version.toString())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Smoke coverage so `./gradlew build` keeps JaCoCo gates satisfied like other Java modules.
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
