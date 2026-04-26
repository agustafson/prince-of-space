# Prince of Space - Project Priorities

> **Status:** Historical priorities snapshot.
> The current canonical decision log is `docs/technical-decision-register.md`.

## Mission

Create a Java code formatter that is beautiful, ergonomic, configurable (within reason), and easy to integrate into any Java project.

## Why This Project Exists

The blog post ["Why are there no decent code formatters for Java?"](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/) summarizes the problem well. Existing Java formatters each fail in at least one critical area:

| Formatter | Main Problems |
|-----------|--------------|
| **google-java-format** | Unconfigurable; 2-space indent (non-standard); excessive double/quadruple indentation; poor lambda formatting |
| **palantir-java-format** | Slightly better lambdas but still unconfigurable; sometimes messy output |
| **Eclipse JDT Formatter** | Requires Eclipse IDE to configure; config is opaque XML; no proper CLI |
| **IntelliJ Formatter** | No CLI access; inconsistent line-breaking; not usable outside the IDE |
| **Prettier Java** | Requires Node.js runtime; unstable between versions; no IntelliJ plugin |
| **Spring Java Format** | Requires `.m2/settings.xml` changes; no CLI tool |

Meanwhile, other ecosystems have excellent formatters: Go has `gofmt`, Rust has `rustfmt`, Python has `black`, JS/TS has `prettier`. Java deserves the same.

## Priority Stack (Ordered)

### P0 - Non-Negotiable

1. **Deterministic & Repeatable** - Same input always produces same output, regardless of environment
2. **Java Language Level Support** - Accept any Java version as a parameter (Java 8 through 25+), passed to the AST parser
3. **Beautiful Output** - Code must be aesthetically appealing and readable; reduced indentation
4. **Fast Performance** - Formatting should be near-instantaneous for typical files

### P1 - Core Requirements

5. **Sensible Defaults** - Works out of the box with zero configuration
6. **Small Configuration Surface** - A curated set of meaningful options (see formatting decisions doc)
7. **Core + Core-Bundled Modules** - `core` with transitive deps, `core-bundled` with shadowed fat jar
8. **Spotless Integration** - Primary build-tool integration via Spotless `FormatterStep` (Spotless provides Maven/Gradle plugins)

### P2 - Important Integrations

9. **IntelliJ Plugin** - IDE integration for format-on-save
10. **VS Code Extension** - IDE integration for format-on-save
11. **CLI Tool** - Standalone command-line formatter

### P3 - Nice to Have

12. **EditorConfig Support** - Read settings from `.editorconfig`
13. **Git Hook Integration** - Pre-commit hook support
14. **Incremental Formatting** - Only format changed lines/files

## Build & Compatibility

- **Source code:** Written in Java 21
- **Target compatibility:** Compiled to Java 17 bytecode
- **User codebases:** Accept any Java language level as a parameter (passed to JavaParser). Supported range: Java 8 through Java 25+, accepting any version the parser supports.

## Design Philosophy

### Lessons from Other Formatters

| Formatter | Philosophy | Config Options | Lesson for Us |
|-----------|-----------|---------------|---------------|
| **gofmt** (Go) | Zero config, one true style | 0 | Proves value of consistency; but Java culture expects *some* choice |
| **Black** (Python) | "Uncompromising"; near-zero config | ~5 (line length, magic trailing comma, target version, skip-string-normalization, skip-magic-trailing-comma) | Small option set works; line-length is essential |
| **Prettier** (JS/TS) | Options are frozen; regret adding most | ~15 meaningful ones | Print width, tab width, tabs, trailing commas are universal; too many options breed bikeshedding |
| **rustfmt** (Rust) | Moderate config; stable vs unstable tiers | ~60 (many unstable) | Width heuristics per construct are powerful; tiered stability is smart |
| **palantir-java-format** | Fork of google-java-format with tweaks | 0 | Even small formatting tweaks (lambda handling) are highly valued |

### Our Stance

**"Reasonable defaults, meaningful knobs."**

- We are NOT gofmt (zero options) or rustfmt (60+ options)
- We target a **small, curated configuration surface (currently 7 options)** that covers the formatting decisions developers actually argue about
- Every option must have a clear default that works for the majority
- We will not add options for things that have clear community consensus (e.g., spaces around operators: yes)
- Import management is delegated to Spotless (not our responsibility)

## Technical Architecture Overview

### AST Parser: JavaParser

Lightweight, purpose-built for parsing, has excellent comment preservation, and its pretty-printer infrastructure gives us a head start. We don't need type resolution for formatting. The Java language level parameter is passed directly to JavaParser's `ParserConfiguration.setLanguageLevel()`.

### Spotless Integration Strategy

Spotless's `FormatterStep` interface is a simple `String format(String rawUnix, File file)` function.

1. **Start with standalone module** - Ship a `prince-of-space-spotless` module that implements `FormatterStep`
2. **No PR needed initially** - users can use Spotless's `custom` step to integrate immediately
3. **Propose upstream later** - Once stable, propose first-class inclusion in Spotless repo

### Module Structure

```
prince-of-space/
  core/              -- Core formatting engine + API (transitive deps: javaparser, slf4j)
  core-bundled/      -- Shadowed fat jar (no transitive deps)
  spotless/          -- Spotless FormatterStep integration
  intellij-plugin/   -- IntelliJ IDEA plugin (future)
  vscode-extension/  -- VS Code extension (future)
  cli/               -- Command-line tool (future)
```

### Logging

Use **SLF4J** API in `core`. Users bring their own SLF4J binding. `core-bundled` will shade SLF4J.
