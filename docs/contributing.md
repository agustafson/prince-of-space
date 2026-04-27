# Contributing

## Conventional Commits

Use [Conventional Commits](https://www.conventionalcommits.org/) so release version inference (Nyx) and changelogs stay consistent.

Examples:

- `feat: add folding strategy option`
- `fix: handle parse error message`
- `chore: bump javaparser`

With **squash merge**, the **PR title** should follow the convention (it becomes the merge commit message).

### How commit types affect releases (Nyx)

Nyx looks at commits **after the latest `v*.*.*` tag** and applies the **strongest** SemVer bump among them: **major** (breaking) > **minor** (`feat`) > **patch** (`fix`, `chore`, `ci`, `docs`, …). So:

- A release that includes **any** `feat:` will be a **minor** bump even if most commits are `fix:` or `chore:`. That is expected.
- To get **patch** releases more often, use release windows where the only bump-worthy types are **patch-level** (for example `fix:`, `chore:`, `ci:`, `refactor:`) and avoid mixing in `feat:` for the same line release—**or** ship `feat` work in its own release.

### Showroom, examples, and golden files

`examples/inputs/**/FormatterShowcase.java` and `examples/outputs/**` are the public **showcase of formatter output**, not just internal tests. Do **not** default those changes to `fix:` unless you are strictly correcting **wrong** output.

**Heuristic — new showroom scenario → usually `feat`:** If you add a **new numbered scenario** in `FormatterShowcase.java` (or materially expand the showroom story), that almost always means the work is **broad and user-visible**—a bigger correction that covers more cases—so prefer **`feat:`**. It matches releases such as `7e619f8` (scenarios 54–55) better than a narrow `fix:`. A **one-off** layout bug with **no** new scenario can still be **`fix:`** when the story is “wrong output, tight patch.”

| Situation | Prefer |
|-----------|--------|
| **Bug**: formatter output was **wrong** relative to the **already intended** rule; goldens update to the corrected behavior | `fix:` — **Fixed** in the changelog. |
| **New numbered scenario** or **large** showcase expansion (see heuristic above) | `feat:` — **minor** bump; **Added** in the changelog. |
| **Substantive** change to `docs/canonical-formatting-rules.md` (redefined **Rule 1–10** behavior, removed or added **public** knobs, new normative requirements that change the product contract) | `feat!:` and a `BREAKING CHANGE:` **footer** when users must act — e.g. `bd21397` (remove `continuationIndentSize`, TDR-014), `846fa82` (wrapped method-chain indent, TDR-015). |
| **Small** clarification or a **narrow** rule tweak in `docs/canonical-formatting-rules.md` that mainly **documents a bugfix** (old output was incorrect or an invariant like idempotency failed) | `fix:` or **`feat:`** if the stress is on newly written normative detail — e.g. `db0658c` (enum idempotence) stayed **`fix:`**; **`feat:`** is optional when a minor line **extends** the written rule without redefining the whole contract. |
| **Breaking** in the SemVer sense: integrators who diff goldens, pin baselines, or depend on old output or API **must** react | `feat!:` / `BREAKING CHANGE:` — **major** when applicable; not every golden diff is a major (see [releasing](releasing.md) and TDR-018). |

Use **`chore:`** only for **mechanical** golden churn with **no** intended output change (rare: for example re-running regeneration after a no-op line-ending fix). If the formatted bytes change, pick **`fix`**, **`feat`**, or **breaking** as above.

## Git and hooks

This repository does **not** ship custom Git hooks under version control. A fresh clone only has Git’s default **`.sample`** files in `.git/hooks/` (inactive until renamed). Nothing under `.git/hooks/` in your clone is coming from this repository.

## Build (JDK)

Use **JDK 21+** to run Gradle here: the **Error Prone** compiler plugin needs a modern `javac`, while published bytecode stays **Java 17** via `--release 17`. The [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) in `settings.gradle.kts` can auto-download a JDK when none matches the requested toolchain.

## PR checks

CI runs tests, Spotless, Checkstyle, SpotBugs, Error Prone, and dependency health. Keep `./gradlew build` green locally.

## Docs checks

Use one command for local docs verification (same entry point CI uses for GitHub Pages):

```bash
./gradlew docsSite
```

This task creates/updates a local docs virtualenv under `.venv-docs` and runs `mkdocs build --strict --site-dir _site`.

To refresh the interactive comparator after output changes, run:

```bash
./gradlew generateCompareHtml
```

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
