# Prince of Space — AI Agent Instructions

## What is this project?

A configurable Java code formatter. See `README.md` for an overview and `docs/technical-decision-register.md` for decision rationale/history.

## Before writing any code, read

| Document | What it covers |
|----------|---------------|
| `docs/architecture.md` | Package layout, coding conventions, module structure, config options |
| `docs/contributing.md` | Commit conventions, build requirements, PR checks |
| `docs/technical-decision-register.md` | Canonical log of technical/product decisions and rationale |
| `docs/canonical-formatting-rules.md` | Canonical, normative formatter behavior rules (MUST follow for formatter changes) |
| `docs/formatting-rules.md` | User-facing explainer and examples for config knobs and formatting behavior |

## Build commands

```
./gradlew :core:test          # run core tests (fast feedback loop)
./gradlew build               # full build: compile, test, Spotless, Checkstyle, SpotBugs
```

Always run `:core:test` after any change to `modules/core/`. A clean `./gradlew build` is required before a PR.

## Version control and showroom goldens

- Whenever you create **new tracked files** (sources, tests, `examples/` goldens, IDE plugin files, `package-lock.json`, and so on), **stage them explicitly** with `git add` and confirm `git status` is clean for intended changes before committing. Do not leave new files untracked.
- Showroom inputs are **`examples/inputs/<level>/FormatterShowcase.java` only**. After editing any of those files, refresh goldens with: `REGENERATE_SHOWROOM=true ./gradlew :core:test --tests RegenerateShowroomGoldens`. Then re-generate the comparison viewer: `python3 scripts/generate-compare.py`. See `docs/showroom-scenarios.md`.

## Key rules (detail in `docs/architecture.md`)

- Implementation goes in `io.princeofspace.internal`; classes there are **package-private** by default.
- `Formatter` must not import JavaParser or any other third-party library directly.
- Tests for package-private classes live in the same package as the class (`src/test/java/io/princeofspace/internal/`).
- Every test touching `Formatter` must assert idempotency: `format(format(x)) == format(x)`.
- For any formatter behavior change or bugfix, treat `docs/canonical-formatting-rules.md` as the canonical contract. If behavior changes intentionally, update that document in the same PR.
- **Keep rules and documentation aligned:** For any change to **output shape**, update in the same PR: `docs/canonical-formatting-rules.md` (normative Rule 1–10), `docs/formatting-rules.md` if the user-facing story or examples need to change, and `docs/technical-decision-register.md` for policy or public-knob decisions. Keep tests and showroom goldens in sync. The canonical file wins if other docs disagree. Code comments may cite rules (e.g. `R3: …`) but must stay accurate.
