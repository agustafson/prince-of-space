# Prince of Space

<img src="docs/images/prince-of-space.jpg" alt="Prince of Space" align="right" width="140">

[![Maven Central](https://img.shields.io/maven-central/v/io.github.agustafson.princeofspace/prince-of-space-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.agustafson.princeofspace/prince-of-space-core)
[![CI](https://github.com/agustafson/prince-of-space/actions/workflows/ci.yml/badge.svg)](https://github.com/agustafson/prince-of-space/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A simple, configurable Java code formatter which produces well-arranged code — ([`io.github.agustafson.princeofspace`](https://central.sonatype.com/namespace/io.github.agustafson.princeofspace)).

> *Named after the hilariously bad 1959 Japanese sci-fi film featured in [Mystery Science Theater 3000](https://www.imdb.com/title/tt0094517/) — specifically [Season 8, Episode 16](https://mst3k.fandom.com/wiki/MST3K_816_-_Prince_of_Space).

> "I have no powers, but I can skip reasonably well".

## Why another formatter?

Java is almost unique among mainstream languages in having no agreed-upon default formatter. JavaScript has [Prettier](https://prettier.io/). Kotlin has [ktlint](https://pinterest.github.io/ktlint/). Go ships `gofmt` in the standard toolchain. Java has… bike-shedding arguments.

The existing options don't fill the gap well. See here for a good summary: https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/. Google-java-format is completely unconfigurable, and its love of double-indenting produces code which can be difficult to read. The Eclipse and IntelliJ built-in formatters offer hundreds of knobs, which sounds like flexibility but in practice means every team configures them differently and the "formatter" becomes another source of style debates rather than the end of them.

Prince of Space takes its philosophy from Prettier and ktlint: strong, readable defaults with just enough configuration to resolve the handful of things teams genuinely disagree about. Minimal options with sensible defaults.

## Features

- **7 configuration options** covering indentation, line length, wrapping, and trailing commas
- **Single-threshold line length** — one `lineLength` target keeps wrapping predictable and the configuration surface small
- **Idempotent (fixed-point output)** — once formatted, running the formatter again makes no further edits (`format(format(x)) == format(x)`). The default engine **converges** internally (repeat passes until stable within a budget) so one call usually suffices; see Rule 1 in [`docs/canonical-formatting-rules.md`](docs/canonical-formatting-rules.md).
- **Java 8 through 25+** — parses any Java language level; runs on JDK 17+
- **Multiple integrations** — library API, CLI, Spotless plugin, IntelliJ plugin, VS Code extension

## Performance (Spring Framework)

<!-- sync:perf:start -->

_Auto-generated from [`docs/perf-results/spring-framework.md`](docs/perf-results/spring-framework.md) — do not edit between markers; run `./gradlew refreshSpringBenchmarkReadme` or CI._

Wall-clock run on the [Spring Framework](https://github.com/spring-projects/spring-framework) corpus (**d9ecf94**, **9204** files, same path filters as the external eval harness). Each JVM formatter runs in one process with warmup; JVM: **21.0.10+7-LTS**

Prince of Space uses `FormatterConfig.defaults()` (line length **120**, wrap **BALANCED**, Java language level **17**). Other JVM formatters use **AOSP** style to align with `examples/external/outputs/` Spotless comparisons.

## Results

| Formatter | Wall time | ms / file | Failures |
|-----------|-----------|-----------|----------|
| Prince of Space (strict, fixed-point) | 58 s | 6.33 | 0 |
| Prince of Space (fast, single pass) | 23 s | 2.55 | 0 |
| Google Java Format (AOSP) | 27 s | 2.98 | 0 |
| Palantir Java Format (AOSP) | 25 s | 2.72 | 0 |

Full detail and regeneration: `./gradlew :formatter-benchmark:run` with `PRINCE_BENCH_ROOT` pointing at a checkout. _Prettier throughput leg skipped in this run (`PRINCE_BENCH_SKIP_PRETTIER=true`)._ Eclipse JDT is not in this JVM harness (Spotless bootstraps it via Equo); see [`examples/external/outputs/eclipse/`](examples/external/outputs/eclipse/) for showroom output.

_Report date: **2026-05-03**._

<!-- sync:perf:end -->

## Quick Start

### Library (Gradle)

```kotlin
dependencies {
    implementation("io.github.agustafson.princeofspace:prince-of-space-core:2.1.2")
}
```

```java
import io.princeofspace.Formatter;
import io.princeofspace.model.FormatterConfig;

Formatter formatter = new Formatter(FormatterConfig.defaults());
String formatted = formatter.format(sourceCode);
```

### Library (Maven)

```xml
<dependency>
    <groupId>io.github.agustafson.princeofspace</groupId>
    <artifactId>prince-of-space-core</artifactId>
    <version>2.1.2</version>
</dependency>
```

### CLI

```bash
./gradlew :cli:shadowJar
java -jar modules/cli/build/libs/prince-of-space-cli-*.jar --help
```

Common flags:

| Flag | Description |
|------|-------------|
| `--check` | Exit 1 if any file would change (no writes) |
| `--stdin` | Read from stdin, write to stdout |
| `--java-version N` | Java language level (8, 11, 17, 21, 25, etc.) |
| `-r` | Recurse into directories |
| `-v` | Verbose progress on stderr |

### Spotless

```kotlin
import io.princeofspace.model.FormatterConfig
import io.princeofspace.spotless.PrinceOfSpaceStep

spotless {
    java {
        target("src/**/*.java")
        addStep(PrinceOfSpaceStep.create(FormatterConfig.defaults()))
    }
}
```

Put the Spotless module on the classpath where your build imports `PrinceOfSpaceStep` — for example `buildSrc` / `implementation`, or `buildscript { dependencies { classpath(...) } }` depending on your Gradle layout. Use `io.github.agustafson.princeofspace:prince-of-space-spotless:2.1.2` (pin to the version on Maven Central). Maven: add the same coordinate as a dependency of `spotless-maven-plugin`, then use `PrinceOfSpaceStep.create(...)` in the plugin configuration.

### IntelliJ Plugin

**Settings > Tools > Prince of Space** — configure all 7 options, choose a fixed Java level or inherit from the module, and optionally enable format-on-save. Format via **Code > Reformat with Prince of Space...**

```bash
./gradlew :intellij-plugin:runIde      # develop
./gradlew :intellij-plugin:buildPlugin  # package
```

### VS Code Extension

The `modules/vscode-extension/` directory contains a TypeScript extension that registers a Java formatting provider. It delegates to the CLI shadow JAR, resolving `modules/cli/build/libs/prince-of-space-cli-*.jar` from the workspace unless `princeOfSpace.cliJar` is set.

## Configuration

| Option | Default | Description                                           |
|--------|---------|-------------------------------------------------------|
| `wrapStyle` | `balanced` | `wide`, `balanced`, or `narrow` wrapping              |
| `indentStyle` | `spaces` | `spaces` or `tabs`                                    |
| `indentSize` | `4` | Units per indent level (spaces or tabs)               |
| `lineLength` | `120` | Target line width — wrapping is triggered here        |
| `closingParenOnNewLine` | `true` | Closing `)` on its own line when args wrap            |
| `trailingCommas` | `false` | Trailing commas in multi-line enums/arrays            |
| `javaLanguageLevel` | `17` | Java syntax level accepted by the parser              |

Numeric and enum defaults in this table match the public `FormatterConfig.DEFAULT_*` constants in source (single source of truth).

Continuation indent is always `2 * indentSize` (8 spaces with the default) for delimited list continuations (parameters, arguments, binary expressions, ternaries, etc.). This follows the Oracle/IntelliJ convention and ensures parameters are always visually distinct from the method body. Programmatic access: `FormatterConfig#continuationIndentSize()`. Wrapped method chains use a single `indentSize` step instead — see [`docs/formatting-rules.md`](docs/formatting-rules.md) "Method Chaining" and TDR-015.

### Wrap style

`wrapStyle` controls how elements are distributed across lines once wrapping is triggered. It is the most consequential option — the same code looks very different across styles.

**`balanced`** (default) — All-or-nothing: either everything fits on one line, or each element gets its own line. This is [Prettier's approach](https://prettier.io/docs/option-philosophy): it avoids the messy middle ground where some arguments are on one line and others on the next.

```java
// fits on one line — left alone
doSomething(name, age, active);

// does not fit — every element gets its own line
doSomething(
        name,
        age,
        active
);
```

**`wide`** — Keep as much on one line as possible; only wrap what is needed to stay within the line length limits.

```java
doSomething(name, age,
        active, extraParam);
```

**`narrow`** — If any wrapping is needed, put every element on its own line immediately.

```java
doSomething(
        name,
        age,
        active
);
```

The `javaLanguageLevel` (default: `17`) controls which Java syntax the parser accepts. Set via `FormatterConfig.builder().javaLanguageLevel(JavaLanguageLevel.of(21))` in the API, or `--java-version 21` on the CLI.

## Examples

The `examples/` directory is the best way to evaluate how options affect real output:

- **`examples/inputs/java{8,17,21,25}/FormatterShowcase.java`** — a single unformatted source file covering 46+ scenarios: constructors, method chains, lambdas, binary operators, generics, switch expressions, records, sealed types, text blocks, and more.
- **`examples/outputs/java{8,17,21,25}/`** — 6 formatted versions per Java level (24 total), one for each combination of `wrapStyle` and `closingParenOnNewLine`.
- **`examples/external/outputs/`** — the same showcase passed through [Spotless](https://github.com/diffplug/spotless) with other popular Java formatters (Google Java Format AOSP, Eclipse JDT, Palantir Java Format AOSP, Prettier with prettier-plugin-java), one file per Java level per formatter — useful when comparing formatters.

Open **[`examples/compare.html`](examples/compare.html)** in a browser (or the GitHub Pages copy for this repo) for an interactive side-by-side diff: choose a Java level, then pick any two outputs on the left and right — Prince of Space configurations **or** one of the Spotless-driven alternatives above — to see how they differ on identical input. For a narrated walkthrough focused on Prince of Space options, see **[docs/output-showcase.md](docs/output-showcase.md)**.

## API

The public API consists of four types:

| Type | Description |
|------|-------------|
| `Formatter` | Entry point — `format(String)` throws on failure, `formatResult(String)` returns a sealed result |
| `FormatterConfig` | Immutable record with builder for all 7 options + language level |
| `FormatResult` | Sealed interface: `Success` or `Failure` (`ParseFailure`, `EmptyCompilationUnit`) |
| `FormatterException` | Thrown by `Formatter.format()` on parse or pipeline failure |

Supporting value types: `IndentStyle`, `WrapStyle`, `JavaLanguageLevel`.

### Strict default vs fast single-pass (`Formatter` overload)

`new Formatter(FormatterConfig)` runs the engine until output **converges** (fixed point within `prince.maxConvergencePasses`), which is what makes **Rule 1** idempotency hold for normal use.

`new Formatter(FormatterConfig, boolean fastSinglePass)` with `fastSinglePass=true` performs **one** parse→print pass — useful for throughput benchmarks (`:formatter-benchmark`) comparing against other single-invocation JVM formatters. It is **not** a second public configuration knob alongside the seven formatting options: exposing “fast” in `FormatterConfig`, Spotless, or IDE plugins would tempt integrations to ship non-fixed-point output for speed. Prefer keeping fast mode for experiments and advanced callers who accept that tradeoff (see TDR-026 in [`docs/technical-decision-register.md`](docs/technical-decision-register.md)).

### Non-throwing API

```java
FormatResult result = formatter.formatResult(sourceCode);
if (result instanceof FormatResult.Success success) {
    System.out.println(success.formattedSource());
} else if (result instanceof FormatResult.ParseFailure failure) {
    System.err.println(failure.message());
}
```

## Artifacts (Maven Central)

Group ID: **`io.github.agustafson.princeofspace`**. Published versions appear on [Maven Central](https://central.sonatype.com/namespace/io.github.agustafson.princeofspace). Quick Start coordinates are synced from **`readmeMavenCoordinatesVersion`** in `gradle.properties` (last Central release — run `./gradlew syncReadmeVersions` after changing it). Development builds use `version=` in the same file (often `*-SNAPSHOT`) and do not appear in README snippets.

| Artifact | Coordinate | When to use |
|----------|------------|-------------|
| `prince-of-space-core` | `io.github.agustafson.princeofspace:prince-of-space-core:2.1.2` | Default — small footprint; JavaParser + SLF4J as normal transitives |
| `prince-of-space-bundled` | `io.github.agustafson.princeofspace:prince-of-space-bundled:2.1.2` | Single fat JAR, dependencies relocated — no classpath clashes |
| `prince-of-space-spotless` | `io.github.agustafson.princeofspace:prince-of-space-spotless:2.1.2` | Spotless `FormatterStep` (`PrinceOfSpaceStep`) |
| CLI (shadow JAR) | Build from repo or attach to [GitHub Releases](https://github.com/agustafson/prince-of-space/releases) | Command-line formatting; not always published to Central |

## Non-goals

- Organisation of Java imports (delegated to Spotless)
- First-party Maven/Gradle plugins (Spotless provides those)
- Type resolution (not needed for formatting)

## Building from source

Requires JDK 21+. Published bytecode targets Java 17 via `--release 17`.

```bash
./gradlew build                # full build: compile, test, Spotless, Checkstyle, SpotBugs
./gradlew :core:test           # fast feedback loop for core changes
```

See [docs/contributing.md](docs/contributing.md) for commit conventions and PR requirements.

## Documentation

See **[docs/index.md](docs/index.md)** for a full index. Key documents:

| Document | Contents |
|----------|----------|
| [docs/architecture.md](docs/architecture.md) | Package layout, coding conventions, module structure |
| [docs/contributing.md](docs/contributing.md) | Commit conventions, build requirements, PR checks |
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting |
| [docs/formatting-rules.md](docs/formatting-rules.md) | All formatting rules and configuration options |
| [docs/evaluation.md](docs/evaluation.md) | Real-world eval harness and latest results (Guava + Spring) |
| [docs/benchmarks.md](docs/benchmarks.md) | Throughput harness (`:formatter-benchmark`) and smoke timings |
| [docs/perf-results/spring-framework.md](docs/perf-results/spring-framework.md) | Latest Spring Framework corpus comparison vs other formatters |
| [docs/technical-decision-register.md](docs/technical-decision-register.md) | Architectural decision log |

## License

[Apache License 2.0](LICENSE)
