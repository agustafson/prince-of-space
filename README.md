# Prince of Space

<img src="docs/images/prince-of-space.jpg" alt="Prince of Space" align="right" width="140">

A beautiful, configurable Java code formatter.

> *Named after the delightfully bad 1959 Japanese sci-fi film featured in [Mystery Science Theater 3000](https://www.imdb.com/title/tt0094517/) — specifically [Season 8, Episode 16](https://mst3k.fandom.com/wiki/MST3K_816_-_Prince_of_Space).
> The Prince's defining trait: **your weapons have no effect on him**. This formatter feels similarly about your inconsistent indentation.*

## Why another formatter?

Java is almost unique among mainstream languages in having no agreed-upon default formatter. JavaScript has [Prettier](https://prettier.io/). Kotlin has [ktlint](https://pinterest.github.io/ktlint/). Go ships `gofmt` in the standard toolchain. Java has… bike-shedding arguments.

The existing options don't fill the gap well. Google-java-format is completely unconfigurable — great if your team already agrees with Google's choices, a non-starter if you don't. The Eclipse and IntelliJ built-in formatters offer hundreds of knobs, which sounds like flexibility but in practice means every team configures them differently and the "formatter" becomes another source of style debates rather than the end of them.

Prince of Space takes its philosophy from Prettier and ktlint: strong, readable defaults with just enough configuration to resolve the handful of things teams genuinely disagree about. Minimal options with sensible defaults.

## Features

- **8 configuration options** covering indentation, line length, wrapping, and trailing commas
- **Two-threshold line length** — a soft preferred width and a hard maximum target, so wrapping looks natural
- **Idempotent** — formatting already-formatted code produces identical output (`format(format(x)) == format(x)`)
- **Java 8 through 25+** — parses any Java language level; runs on JDK 17+
- **Multiple integrations** — library API, CLI, Spotless plugin, IntelliJ plugin, VS Code extension

## Quick Start

### Library (Gradle)

```kotlin
dependencies {
    implementation("io.github.agustafson:prince-of-space-core:VERSION")
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
    <groupId>io.github.agustafson</groupId>
    <artifactId>prince-of-space-core</artifactId>
    <version>VERSION</version>
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
| `--stdin` / `--stdout` | Read from stdin, write to stdout |
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

Ensure `prince-of-space-spotless` (which transitively pulls in `prince-of-space-core`) is on the Spotless plugin classpath — via `buildscript` dependencies, a dedicated `buildSrc` dependency, or your Gradle version's supported mechanism.

For Maven, add `prince-of-space-spotless` as a dependency of `spotless-maven-plugin` and configure a custom step using `PrinceOfSpaceStep.create(...)`.

### IntelliJ Plugin

**Settings > Tools > Prince of Space** — configure all 8 options, choose a fixed Java level or inherit from the module, and optionally enable format-on-save. Format via **Code > Reformat with Prince of Space...**

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
| `preferredLineLength` | `120` | Soft target — wrapping starts here                    |
| `maxLineLength` | `150` | Hard target — exceeded only when no wrap point exists |
| `continuationIndentSize` | `4` | Units for continuation lines                          |
| `closingParenOnNewLine` | `true` | Closing `)` on its own line when args wrap            |
| `trailingCommas` | `false` | Trailing commas in multi-line enums/arrays            |

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
- **`examples/outputs/java{8,17,21,25}/`** — 12 formatted versions per Java level (48 total), one for each combination of `wrapStyle`, `continuationIndentSize`, and `closingParenOnNewLine`.

For an interactive side-by-side diff, open **[`examples/compare.html`](examples/compare.html)** in a browser (or visit the hosted version at the GitHub Pages URL for this repo) — pick a Java version and two configurations to compare. For a narrated walkthrough of the key differences, see **[docs/output-showcase.md](docs/output-showcase.md)**.

## API

The public API consists of four types:

| Type | Description |
|------|-------------|
| `Formatter` | Entry point — `format(String)` throws on failure, `formatResult(String)` returns a sealed result |
| `FormatterConfig` | Immutable record with builder for all 8 options + language level |
| `FormatResult` | Sealed interface: `Success` or `Failure` (`ParseFailure`, `EmptyCompilationUnit`) |
| `FormatterException` | Thrown by `Formatter.format()` on parse or pipeline failure |

Supporting value types: `IndentStyle`, `WrapStyle`, `JavaLanguageLevel`.

### Non-throwing API

```java
FormatResult result = formatter.formatResult(sourceCode);
if (result instanceof FormatResult.Success success) {
    System.out.println(success.formattedSource());
} else if (result instanceof FormatResult.ParseFailure failure) {
    System.err.println(failure.message());
}
```

## Artifacts

| Artifact | When to use |
|----------|-------------|
| `prince-of-space-core` | Default — small footprint, you manage JavaParser/SLF4J versions |
| `prince-of-space-bundled` | Single fat JAR with relocated dependencies — no transitive conflicts |
| `prince-of-space-spotless` | Spotless `FormatterStep` integration |
| `prince-of-space-cli` | Shadow JAR for command-line use |

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

See [CONTRIBUTING.md](CONTRIBUTING.md) for commit conventions and PR requirements.

## Documentation

See **[docs/README.md](docs/README.md)** for a full index. Key documents:

| Document | Contents |
|----------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Package layout, coding conventions, module structure |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Commit conventions, build requirements, PR checks |
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting |
| [docs/formatting-rules.md](docs/formatting-rules.md) | All formatting rules and configuration options |
| [docs/evaluation.md](docs/evaluation.md) | Real-world eval harness and latest results (Guava + Spring) |
| [docs/technical-decision-register.md](docs/technical-decision-register.md) | Architectural decision log |

## License

[Apache License 2.0](LICENSE)
