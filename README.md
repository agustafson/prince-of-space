# Prince of Space

A beautiful, configurable Java code formatter.

> **Under Construction** — This project is in active development and not yet ready for use.

## What is this project?

A Java code formatter library that produces beautiful, readable output with a small set of meaningful configuration options. It aims to solve the problems with existing formatters (google-java-format, palantir-java-format, etc.) which are either unconfigurable or produce ugly output.

## Key Documents

- `IMPLEMENTATION_PLAN.md` — Phased implementation plan with detailed tasks
- `docs/01-project-priorities.md` — Project goals, architecture, module structure
- `docs/02-formatting-decisions.md` — All formatting rules, 8 configuration options, and decided behaviors
- `docs/03-research-notes.md` — Research on other formatters, AST parsers, Spotless integration

## Non-goals

- Import organization (delegated to Spotless)
- Maven/Gradle plugins (Spotless provides those)
- Type resolution (not needed for formatting)
