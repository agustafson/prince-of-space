# Repository rulesets

GitHub does not load repository rulesets from this directory automatically. `main.json` is the
source-controlled configuration for the active repository ruleset named `main`.

The required check contexts must match the emitted job names in
`.github/workflows/ci.yml`. The Java matrix reports three independent checks: `Java 17`, `Java 21`,
and `Java 25`.

Apply the checked-in configuration after editing it:

```bash
scripts/apply-main-ruleset.sh agustafson/prince-of-space
```

This requires the GitHub CLI (`gh`) to be authenticated as an account with permission to manage
repository rulesets. Merging changes to `main.json` does not itself update the live GitHub settings.
