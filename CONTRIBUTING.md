# Contributing

## Conventional Commits

Use [Conventional Commits](https://www.conventionalcommits.org/) so release version inference (Nyx) and changelogs stay consistent.

Examples:

- `feat: add folding strategy option`
- `fix: handle parse error message`
- `chore: bump javaparser`

With **squash merge**, the **PR title** should follow the convention (it becomes the merge commit message).

## Git and hooks

This repository does **not** ship custom Git hooks under version control. A fresh clone only has Git’s default **`.sample`** files in `.git/hooks/` (inactive until renamed). Nothing under `.git/hooks/` in your clone is coming from this repository.

## Build (JDK)

Use **JDK 21+** to run Gradle here: the **Error Prone** compiler plugin needs a modern `javac`, while published bytecode stays **Java 17** via `--release 17`. The [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) in `settings.gradle.kts` can auto-download a JDK when none matches the requested toolchain.

## PR checks

CI runs tests, Spotless, Checkstyle, SpotBugs, Error Prone, and dependency health. Keep `./gradlew build` green locally.

## Dependency declarations

- Do not use dynamic versions (`latest.release`, `+`, ranges).
- Declare dependency coordinates and versions in `gradle/libs.versions.toml`.
- Keep each `libs.versions.toml` section alphabetically ordered by key.

## Code

- Prefer **small public API** changes — every public type is a compatibility promise.
- **JSpecify** nullability on public API; **NullAway** runs as **ERROR** on `compileJava` for `io.princeofspace` (main sources only; disabled on `compileTestJava`).
- Any formatting behavior change should add/update a representative case in `examples/inputs/*/FormatterShowcase.java`
  and regenerate `examples/outputs/*` (goldens). This keeps human-readable showcase coverage aligned with
  regression tests.

## Rules and documentation alignment

When you change what the formatter **outputs** (not just refactors or performance), keep **tests, normative docs, and user-facing docs** aligned so the project has a single source of truth.

| What you change | What to update |
|------------------|----------------|
| **Normative output contract** (what “correct” formatting is) | `docs/canonical-formatting-rules.md` — this file wins if other prose disagrees. Update **Rule 1–10** (or their sub-bullets) in the same PR as the code when behavior is intentional. |
| **User-facing explanation or examples** (config knobs, “why it looks this way”) | `docs/formatting-rules.md` so users and the showroom story stay in sync with the engine. |
| **Policy or a new/removed public knob, or a significant “why we did this”** | Append a record in `docs/technical-decision-register.md` (TDR). New public `FormatterConfig` options require a TDR per the canonical doc’s change control. |
| **Regression and showcase** | Unit/integration tests; refresh showroom goldens when `examples/inputs/.../FormatterShowcase.java` or normative rules warrant it. |

**Agents and maintainers:** implementation code in `io.princeofspace.internal` may reference canonical rules in comments (for example `R3: …` for Rule 3 in `docs/canonical-formatting-rules.md`) to tie behavior to the contract. If you add such references, they must stay accurate when the rules change.

**Precedence:** `docs/canonical-formatting-rules.md` is the normative contract. `docs/formatting-rules.md` is the friendly guide; it must not contradict the canonical file.
