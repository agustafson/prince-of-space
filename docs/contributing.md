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

### GitHub Actions pushes to `main` (README sync, benchmark refresh, release housekeeping)

Workflows that commit back to the repo use **`permissions: contents: write`** and checkout with `token: ${{ secrets.GH_ACTIONS_PUSH_TOKEN || github.token }}` (the PAT is used when the secret exists).

**Why `GITHUB_TOKEN` is not enough on a protected `main`:** The default token authenticates as **`github-actions[bot]`**. That identity does **not** inherit *your* admin bypass, and rulesets do not list a generic “GitHub Actions” actor. Pushes can fail with `GH013: Repository rule violations` even with **Read and write** enabled. The same identity restriction also means **`GITHUB_TOKEN` can never submit an approving review** — GitHub blocks Actions-authenticated tokens from approving any PR, including ones the workflow itself is acting on, specifically to stop a workflow from rubber-stamping its own merge. If a ruleset requires an approving review (ours does — see `dependabot-auto-merge.yml`), only a real-user PAT like `GH_ACTIONS_PUSH_TOKEN` can supply it.

Use the two steps below when you need **direct pushes** to `main` from automation, or an approving review for Dependabot auto-merge.

#### Step 1 — Create a fine-grained PAT and add it as `GH_ACTIONS_PUSH_TOKEN`

These substeps use **your GitHub account** (or a dedicated **machine user** — same flow, but every mention of “you” is that account).

1. Open **GitHub → your avatar (top right) → Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. **Resource owner:** your account (or the org that owns `prince-of-space`, if the token must be org-scoped — match where the repo lives).
3. **Repository access:** **Only select repositories**, then pick **`agustafson/prince-of-space`** (adjust if the repo path differs).
4. **Permissions → Repository permissions:**
   - **Contents:** **Read and write** (required for `git push`).
   - **Pull requests:** **Read and write** (required to approve Dependabot PRs — see below).
   - Leave everything else **No access** unless you know you need it (least privilege).
5. **Expiration:** choose something you can rotate (e.g. 90 days or 1 year). Put a calendar reminder to regenerate and update the secret before expiry.
6. **Generate** and **copy the token once** (GitHub will not show it again).
7. In the repo: **Settings → Secrets and variables → Actions → New repository secret**.
   - **Name:** `GH_ACTIONS_PUSH_TOKEN` (must match exactly — workflows reference this name).
   - **Value:** paste the token → **Add secret**.

After the next workflow run, checkout uses this token, so **git operations run as the PAT owner’s account** (committer/app identity depends on git config in the workflow; your README workflows set `user.name` / `user.email` for `github-actions[bot]` for display only — the **authentication** is still the PAT).

**Optional machine user:** Create a second GitHub account, add it as a **collaborator** with **Write** (or **Maintain**) on the repo, generate the PAT while logged in as that user, and use that token for `GH_ACTIONS_PUSH_TOKEN`. Then Step 2 adds **that** user to bypass, keeping automation separate from your personal account.

#### Step 2 — Let that identity bypass the rules that block direct pushes

Rules apply to **who is pushing**, not which secret name you used. The account that owns the PAT must be allowed to push to `main` under your rules.

**Note:** this step is only for direct pushes/merges. Submitting an approving review (as `dependabot-auto-merge.yml` does) is not a protected action under branch rules, so the PAT's account does **not** need to be on the bypass list for that to work — Step 1's **Pull requests: Read and write** permission is sufficient on its own.

**If you use Repository rules** ( **Settings → Rules → Rulesets** , or org-level rulesets that include this repo):

1. Open the **ruleset** that targets **`main`** (or `refs/heads/main` / default branch).
2. Find **Bypass list** (wording may be **Bypass actors** or **Add bypass** depending on UI version).
3. **Add bypass** and choose an actor type GitHub offers — commonly:
   - **Repository role** → e.g. **Admin** (only if the PAT user is admin — avoid if you use your normal user and do not want automation tied to admin), or
   - **Team** (if the PAT user is only in a team you add to bypass), or
   - Most directly for a **personal PAT:** add the **user account** that owns the PAT if the UI lets you pick **People** / **Repository collaborator** (exact labels vary).
4. Set **Bypass mode** to **Always** (not only “on pull request”) if you need **direct commits** to `main`.
5. Save the ruleset.

**If you still cannot find the PAT’s user in the list:** Some UIs only offer **roles, teams, and GitHub Apps**. Then either (a) invite that user with a role that appears in **Repository role** bypass (e.g. make the machine user **Admin** on this repo only — weigh risk), (b) put that user in a **team** and add **Team** to bypass, or (c) avoid bypass entirely and change the workflow to **push to a branch** and **merge via PR** (with optional auto-merge).

**Classic branch protection** (older **Settings → Branches → Branch protection rule**): use **Restrict who can push** / allowlisted users or teams, or relax **Require a pull request before merging** for automation — prefer **rulesets** + bypass on new setups.

#### After both steps

Re-run a workflow that pushes (e.g. **Sync README coordinates** or push a no-op `gradle.properties` change). If it still fails, open the failed job log and search for **`GH013`** or **`remote: error`** — the message lists which rule blocked the push.

### `:core` test entry points

| Gradle task | What runs |
|---------------|-----------|
| `./gradlew :core:test` | Default unit/integration suite (JUnit tags exclude `eval`). |
| `./gradlew :core:evalTest` | Tagged **`eval`** harness (needs corpus env — see task description). Uses configuration-cache-friendly environment wiring. |
| `./gradlew :core:showroomGoldenTest` | Tagged **`showroom-golden`** — asserts committed `examples/outputs/**` showroom bytes. |

Plain `./gradlew assemble` at the repo root builds JVM artifacts only. To also build the MkDocs site (Python), run `./gradlew assembleWithDocs` or `./gradlew generateDocs` (same targets CI uses for docs publication).

## Docs checks

Use one command for local docs verification (same entry point CI uses for GitHub Pages):

```bash
./gradlew docsSite
```

This task runs the Gradle MkDocs plugin (with dependencies from `docs/requirements.txt`) and performs `mkdocs build --strict --site-dir _site`.

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
