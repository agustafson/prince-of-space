# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **CI** — Release workflow uses Nyx again for version inference, pinned to a stable action version and configured so conventional `chore/docs/style/refactor/test/ci/perf/build` commits count as patch bumps.

### Fixed

- **Maven publication:** set explicit `artifactId` on `:core` and `:spotless` publications (`prince-of-space-core`, `prince-of-space-spotless`). Gradle’s default was the subproject name (`core`, `spotless`). The bundled artifact already used `prince-of-space-bundled`.

## [0.1.0] — 2026-04-18

First release on **Maven Central** (`io.github.agustafson`).

### Added

- **Core formatter** — AST-based Java code formatter using JavaParser, with convergence loop (up to 64 passes) guaranteeing idempotent output.
- **8 configuration options** — `indentStyle`, `indentSize`, `preferredLineLength`, `maxLineLength`, `continuationIndentSize`, `wrapStyle`, `closingParenOnNewLine`, `trailingCommas` — exposed via `FormatterConfig` record with builder.
- **Two-threshold line length** — soft preferred width triggers wrapping; hard maximum target is exceeded only when no wrap point exists.
- **`JavaLanguageLevel` record** — first-party type for language level configuration, replacing direct use of JavaParser's `LanguageLevel` in the public API. Supports any Java version (1–25+) with optional preview flag.
- **Sealed `FormatResult` API** — `Formatter.formatResult()` returns `FormatResult.Success` or `FormatResult.Failure` (subtypes: `ParseFailure`, `EmptyCompilationUnit`) for non-throwing error handling.
- **CLI** (`:cli`) — Picocli-based command-line tool with `--check`, `--stdin`/`--stdout`, `--java-version`, recursive directory formatting, and git-aware file discovery.
- **Spotless integration** (`:spotless`) — `PrinceOfSpaceStep` implementing Spotless `FormatterStep` with serializable configuration for classloader isolation.
- **IntelliJ plugin** (`:intellij-plugin`) — Settings UI for all 8 options, language level from module or fixed release, optional format-on-save, and **Code > Reformat with Prince of Space...** action.
- **VS Code extension** (`modules/vscode-extension/`) — TypeScript extension registering a Java formatting provider, delegating to the CLI shadow JAR.
- **Bundled artifact** (`:core-bundled`) — Shadow JAR with all dependencies relocated under `io.princeofspace.shaded.*` for environments requiring zero transitive dependencies.
- **Formatting pipeline** — Parse > LexicalPreservingPrinter > BraceEnforcer > AnnotationArranger > PrettyPrinter > BlankLineNormalizer, with width-aware wrapping for method chains, argument lists, logical operators, type clauses, switch labels, lambda parameters, and generic type arguments.
- **Comment preservation** — line, block, Javadoc, end-of-line, between-statement, and type-use comments preserved through formatting.
- **Showroom golden tests** — 4 Java levels (8, 17, 21, 25) x 12 config combinations = 48 golden files in `examples/inputs/` and `examples/outputs/`.
- **Test suite** — 200+ tests including unit tests for internal components (`FormattingEngine`, `AnnotationArranger`), wrapping regressions, comment preservation, idempotency fuzz testing, and examples corpus validation.
- **Real-world eval harness** — validated against Guava (3,221 files) and Spring Framework (9,198 files) with zero parse errors and zero idempotency failures.
- **Static analysis** — Error Prone, NullAway, SpotBugs, Checkstyle, and Spotless enforced in CI.
- **CI** — GitHub Actions: build on push/PR across Java 17, 21, and 25; manual release workflow with Nyx version inference and Sonatype Central Portal upload.
- **Documentation** — architecture guide, formatting decisions, technical decision register, contributing guide, showroom scenarios, benchmarks, and robustness harness docs.

### Fixed

- Idempotency across trailing comments, wrapped chains, lambda-call indentation, string literal wrapping, and comment-adjacent spacing.
- IntelliJ plugin `untilBuild` set to unbounded (`provider { null }`) for forward compatibility.
- `maxLineLength` documentation clarified as a hard target (best-effort) rather than an absolute guarantee.
