# Prince of Space — AI Agent Instructions

## What is this project?

A Java code formatter library that produces beautiful, readable output with a small set of meaningful configuration options. It aims to solve the problems with existing formatters (google-java-format, palantir-java-format, etc.) which are either unconfigurable or produce ugly output.

## Key Documents

- `IMPLEMENTATION_PLAN.md` — Phased implementation plan with detailed tasks
- `docs/01-project-priorities.md` — Project goals, architecture, module structure
- `docs/02-formatting-decisions.md` — All formatting rules, 8 configuration options, and decided behaviors
- `docs/03-research-notes.md` — Research on other formatters, AST parsers, Spotless integration

## Build & Compatibility

- **Source code:** Java 21 (use modern language features)
- **Target:** Compile to Java 17 bytecode (`--release 17`)
- **Build tool:** Maven (multi-module)
- **User codebases:** Accept any Java language level as a parameter (Java 8 through 25+)

## Architecture

- **AST Parser:** JavaParser (`com.github.javaparser:javaparser-core`)
- **Logging:** SLF4J API
- **Entry point:** `Formatter.format(String sourceCode) -> String`
- **Config:** `FormatterConfig` immutable value class with builder

## Modules

- `core` — Formatting engine (deps: javaparser, slf4j-api)
- `core-bundled` — Shaded fat jar (no transitive deps)
- `spotless` — Spotless FormatterStep integration
- `cli` — Command-line tool (picocli)

## Configuration Options (8 total)

| Option | Default |
|--------|---------|
| `indentStyle` | `spaces` |
| `indentSize` | `4` |
| `preferredLineLength` | `120` |
| `maxLineLength` | `150` |
| `continuationIndentSize` | `4` |
| `wrapStyle` | `balanced` |
| `closingParenOnNewLine` | `true` |
| `trailingCommas` | `false` |

## Testing

- Golden file tests: `examples/inputs/` → format → compare with `examples/outputs/`
- 3 Java levels (java8, java17, java21) × 12 config combinations = 36 golden files
- Every test must verify idempotency: `format(format(x)) == format(x)`

## Non-goals

- Import organization (delegated to Spotless)
- Maven/Gradle plugins (Spotless provides those)
- Type resolution (not needed for formatting)
