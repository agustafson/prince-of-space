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
- [ ] `CHANGELOG.md` is up to date with all changes since the last release
- [ ] All commits since the last tag follow [Conventional Commits](https://www.conventionalcommits.org/)
      so Nyx can derive the correct next version:
  - `feat:` → minor bump (`0.1.x` → `0.2.0`)
  - `fix:` → patch bump (`0.1.0` → `0.1.1`)
  - `feat!:` or `BREAKING CHANGE:` in footer → major bump

---

## Triggering a release

Release version inference uses the **Nyx GitHub Action** (`mooltiverse/nyx`) with this
repository’s `.nyx.yml` — the **Gradle Nyx plugin is not used**, so local `./gradlew`
stays compatible with the configuration cache. To preview the next version locally, run
the same Nyx container the action uses (see the [Nyx Docker image](https://github.com/mooltiverse/nyx))
or trigger a workflow dry run.

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
1. Infer the next version from git tags + conventional commits (Nyx)
2. Run the full test suite
3. Build and GPG-sign all artifacts (`core`, `spotless`, `core-bundled`)
4. Bundle artifacts and upload to Sonatype Central Portal
5. Poll until the bundle is **VALIDATED** (checksums, signatures, POM requirements)
6. Publish the deployment (artifacts available on Maven Central within ~15 minutes)
7. Push a `vX.Y.Z` git tag and create a GitHub Release with auto-generated notes

---

## Version inference rules (Nyx)

Nyx reads the git log since the last `vX.Y.Z` tag and applies these rules:

| Commit type | Version bump |
|-------------|-------------|
| `feat:` | minor (`0.1.0` → `0.2.0`) |
| `fix:`, `chore:`, `docs:`, `style:`, `refactor:`, `test:`, `ci:` | patch (`0.1.0` → `0.1.1`) |
| `feat!:`, `fix!:`, or `BREAKING CHANGE:` in footer | major (`0.1.0` → `1.0.0`) |

If no releasable commits exist (e.g. only `chore:` commits), Nyx will still infer a
patch version. If the inferred version is the same as the last tag, the workflow will
abort with an error.

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

### Central Portal validation failed

Check the deployment status at <https://central.sonatype.com/publishing/deployments>.
Common causes: missing signatures, malformed POM, missing sources/javadoc. Fix the
root cause, then re-run the workflow.

### Workflow succeeded but artifacts not on Maven Central after 30 minutes

Log in to <https://central.sonatype.com/publishing/deployments>, find the deployment,
and manually click "Publish" if it is stuck in `VALIDATED` state.

### Need to retract a release

Maven Central releases cannot be deleted, but they can be deprecated. Contact Sonatype
support. Prefer publishing a corrected patch release (`X.Y.(Z+1)`) over retraction.
