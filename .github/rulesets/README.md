# Repository rulesets

GitHub does not load repository rulesets from this directory automatically. `main.json` is the
source-controlled configuration for the active repository ruleset named `main`.

The required check contexts must match job names emitted by their respective workflows. In
particular, the Java matrix in `.github/workflows/ci.yml` reports three independent checks:
`Java 17`, `Java 21`, and `Java 25`.

This file manages the repository ruleset only. The one-approval, code-owner, and last-push approval
requirements are configured separately through classic branch protection and are not changed by the
apply script.

The `maintainer-approval` context is an exception to "contexts must match job names": it is a check
run published through the dedicated publisher GitHub App by
[`.github/workflows/maintainer-approval.yml`](../workflows/maintainer-approval.yml), not a job. Its
required entry must set `integration_id` to the publisher app's id so same-named GitHub Actions jobs
cannot satisfy it. See [`.github/maintainer-check/README.md`](../maintainer-check/README.md).

Apply the checked-in configuration after editing it:

```bash
scripts/apply-main-ruleset.sh agustafson/prince-of-space
```

This requires the GitHub CLI (`gh`) to be authenticated as an account with permission to manage
repository rulesets. Merging changes to `main.json` does not itself update the live GitHub settings.
