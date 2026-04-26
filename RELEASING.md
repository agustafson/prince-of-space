# Releasing Prince of Space

This document describes the full release process. The release workflow
(`.github/workflows/release.yml`) automates most steps — this file exists so that
a human or agent can understand, verify, and if necessary recover from any step.

## Prerequisites (one-time setup)

### 1. Register a Sonatype Central Portal account

Go to <https://central.sonatype.com> and sign in with your GitHub account (`agustafson`).
Register the namespace `io.github.agustafson`:

- **Namespace:** `io.github.agustafson`
- **Verification:** Sonatype creates a temporary GitHub repository under your personal
  account; you verify by confirming the repo exists.

After verification, generate a **User Token** (account → Generate User Token). Store
the token — it is the value for `SONATYPE_CENTRAL_TOKEN`.

### 3. Generate a GPG signing key

```bash
gpg --full-generate-key   # RSA, 4096 bits, no expiry recommended for CI
gpg --list-secret-keys --keyid-format LONG
# Note the KEY_ID (e.g. 3AA5C34371567BD2)

# Publish the *public* key so Maven Central validation can resolve it (required).
# If this step is skipped, Central reports that signatures are invalid and the key
# fingerprint cannot be found on a public keyserver.
#
# Prefer HKPS (HTTPS, port 443). Plain `hkp://…` often hangs when port 11371 is blocked
# or the keyserver is slow; see troubleshooting below if send-keys stalls.
gpg --keyserver hkps://keys.openpgp.org:443 --send-keys KEY_ID
gpg --armor --export-secret-keys KEY_ID > private.key
```

Store the contents of `private.key` as the `GPG_PRIVATE_KEY` secret, and the passphrase
you chose as `GPG_PASSPHRASE`. Delete `private.key` from disk after uploading.

To confirm the public key is visible before a release:

```bash
gpg --keyserver hkps://keys.openpgp.org:443 --receive-keys KEY_ID   # should import OK
```

**If `--send-keys` hangs:** add a timeout (`--keyserver-options timeout=20`), or use the
**web upload** at [keys.openpgp.org/upload](https://keys.openpgp.org/upload) (often
simplest): `gpg --armor --export KEY_ID`, paste, submit — no keyserver or `dirmngr`
involved.

**If `gpgconf --kill dirmngr` also hangs:** `gpgconf` is waiting on the same stuck
daemon — use `ps aux | grep dirmngr` / `grep gpg-agent`, then `kill -9` those PIDs (or
`killall -9 dirmngr gpg-agent` on macOS). Socket files under `~/.gnupg/S.*` can be
removed only **after** the processes are dead; GnuPG will recreate them on next use.
Then retry HKPS `send-keys`, or prefer web upload and skip keyservers entirely.

If `keys.openpgp.org` requires it, confirm the email address on the key through their UI.
Keyserver propagation can take minutes to hours; retry validation in the Central Portal
after publishing.

### 4. Add secrets to the repository

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret name            | Value                                          |
|------------------------|------------------------------------------------|
| `SONATYPE_CENTRAL_TOKEN` | Bearer token from central.sonatype.com       |
| `GPG_PRIVATE_KEY`      | Armored GPG private key (`--armor --export-secret-keys`) |
| `GPG_PASSPHRASE`       | Passphrase for the GPG key                    |

### 5. Create the `release` GitHub Actions environment

In **Settings → Environments**, create an environment named `release`. Optionally add
required reviewers so the workflow must be approved before it can publish.

---

## Release checklist

Before triggering the workflow, verify:

- [ ] `main` branch CI is green (`./gradlew build` passes locally or via Actions)
- [ ] Commits/PR titles since the last release follow Conventional Commits so Nyx can generate accurate changelog entries
- [ ] All commits since the last tag follow [Conventional Commits](https://www.conventionalcommits.org/)
      so Nyx can derive the correct next version:
  - `feat:` → minor bump (`0.1.x` → `0.2.0`)
  - `fix:` → patch bump (`0.1.0` → `0.1.1`)
  - `feat!:` or `BREAKING CHANGE:` in footer → major bump

---

## Triggering a release

Release version inference runs **Nyx** in CI: it reads the latest `v*` tag and commit
messages since that tag (same bump rules as the table below). To preview locally, run Nyx
from Docker:

```bash
docker run --rm -v "$(pwd):/repo" -w /repo ghcr.io/mooltiverse/nyx:latest infer --configuration-file=.nyx.yml
```

Ensure tags are fetched first (`git fetch --tags`). Or trigger a workflow **dry run** and
read the log.

You can **override** the version with the workflow input **`release_version`** (e.g. `0.2.0`)
when you need to ship without conventional commits or to correct a mistake.

### Dry run (verify version inference without publishing)

1. Go to **Actions → Release → Run workflow**
2. Check **"Dry run"**
3. Click **Run workflow**

The workflow will infer the version and build+sign all artifacts but will not publish
or tag anything. Check the logs to confirm the inferred version is correct.

### Full release

1. Go to **Actions → Release → Run workflow**
2. Leave **"Dry run"** unchecked
3. Click **Run workflow**

The workflow will:
1. Infer the next version from git tags + conventional commits (see below)
2. Run the full test suite
3. Build and GPG-sign all artifacts (`core`, `spotless`, `core-bundled`)
4. Bundle artifacts and upload to Sonatype Central Portal
5. Poll until the bundle is **VALIDATED** (checksums, signatures, POM requirements)
6. Publish the deployment (artifacts available on Maven Central within ~15 minutes)
7. Build downloadable assets: CLI shadow JAR, IntelliJ plugin ZIP, VS Code `.vsix` (VS Code `package.json` version is set in CI to match the release)
8. Push a `vX.Y.Z` git tag, create a GitHub Release using Nyx-generated release notes, and attach those three files
9. Commit Nyx-generated `CHANGELOG.md` updates and bump `gradle.properties` to the next patch `-SNAPSHOT` (best-effort push; warns on branch protection)

Publishing the IntelliJ plugin to the **JetBrains Marketplace** and the VS Code extension to the **Visual Studio Marketplace** / Open VSX is still **manual** unless you add separate publishing steps and credentials.

---

## Version inference rules (Nyx)

**Source of truth is git**, not `gradle.properties` or Maven Central. Nyx inspects `vX.Y.Z`
tags and commit messages after the latest matching tag. If there are no matching bump-worthy
commits, the inferred version can equal the latest tag and the workflow fails.

The release workflow checks out with **`fetch-depth: 0`** and **`fetch-tags: true`** so tags
and history are present. If **no `v*` tags** exist yet, Nyx uses **`0.1.0`**
(`initialVersion` in `.nyx.yml`).

The `version=` line in `gradle.properties` is a **local / non-release default** (usually
`*.*.*-SNAPSHOT` on the next patch line). The release workflow overrides it with
`ORG_GRADLE_PROJECT_version` while building a release, then **commits** a bump to the next
patch SNAPSHOT (e.g. after shipping `v0.1.0`, main gets `version=0.1.1-SNAPSHOT`). That is
**one `chore:` commit per release** — predictable churn. It does **not** drive CI
inference; **Nyx + tags + conventional commits** do.

If `git push` for that commit fails (e.g. branch protection), allow the GitHub Actions app
to push to `main` or adjust protection rules.

| Commit pattern | Version bump |
|----------------|--------------|
| `feat:` | minor (`0.1.0` → `0.2.0`) |
| `fix:`, `chore:`, `docs:`, `style:`, `refactor:`, `test:`, `ci:`, `perf:`, `build:` | patch (`0.1.0` → `0.1.1`) |
| `type!:` / `type(scope)!:` or `BREAKING CHANGE:` footer | major (`0.1.0` → `1.0.0`) |

The **highest** bump among commits since the last tag wins. If the inferred version equals
the latest `v*` tag, the workflow aborts with an error (nothing new to ship).

---

## What is published to Maven Central

Three artifacts are published under `io.github.agustafson`:

| Artifact ID | Description |
|-------------|-------------|
| `prince-of-space-core` | Core library (normal POM; JavaParser + SLF4J as transitive deps) |
| `prince-of-space-spotless` | Spotless `FormatterStep` integration |
| `prince-of-space-bundled` | Shaded fat JAR with no transitive deps |

Each artifact ships with a `-sources.jar`, `-javadoc.jar`, `.pom`, and `.asc` signature,
satisfying Maven Central requirements.

The IntelliJ plugin is published separately to the JetBrains Marketplace
(`./gradlew :intellij-plugin:publishPlugin`). The CLI shadow JAR is attached to GitHub
Releases as a download artifact (not published to Maven Central).

---

## Recovery

### Inference fails or equals the last tag (`0.1.0` again, or “nothing new to release”)

1. **Confirm the release tag exists on GitHub:** e.g. `v0.1.0` on the commit you meant to
   ship. If the workflow failed **before** “Tag and push”, you may have published to
   Central without creating the tag — create it once:
   `git tag v0.1.0 <commit-sha> && git push origin v0.1.0`
2. **Ensure CI sees tags:** the release workflow uses `actions/checkout` with
   `fetch-tags: true` and full history.
3. **Ensure there are commits after that tag** with **conventional messages** (`fix:`, `feat:`,
   etc.). Reproduce locally with Nyx (Docker command above) after `git fetch --tags`.
   If you must ship without those commits, set workflow input **`release_version`**.

`gradle.properties` is **not** used for inference in CI. Optional: after each release, commit
a higher **`-SNAPSHOT`** in `gradle.properties` if you want the default local version to
reflect the “next patch line”.

### Central Portal validation failed

Check the deployment status at <https://central.sonatype.com/publishing/deployments>.
Common causes: missing signatures, malformed POM, missing sources/javadoc. Fix the
root cause, then re-run the workflow.

If all errors are `already exists` for the same `groupId:artifactId:version`, a previous
run likely published successfully and failed later in the workflow. The release workflow
detects this duplicate-only case and continues (skipping re-publish) so tagging/GitHub
Release can complete.

The release workflow is retry-safe for post-publish steps: it skips tag creation/push if
`vX.Y.Z` already exists, updates an existing GitHub Release by re-uploading assets
(`--clobber`), and skips the `gradle.properties` bump commit when already at the next
snapshot.

If branch protection blocks direct pushes to `main`, the workflow logs a warning and
continues (release remains successful). In that case, apply the `gradle.properties`
`-SNAPSHOT` bump through a normal PR.

### Workflow succeeded but artifacts not on Maven Central after 30 minutes

Log in to <https://central.sonatype.com/publishing/deployments>, find the deployment,
and manually click "Publish" if it is stuck in `VALIDATED` state.

### Need to retract a release

Maven Central releases cannot be deleted, but they can be deprecated. Contact Sonatype
support. Prefer publishing a corrected patch release (`X.Y.(Z+1)`) over retraction.
