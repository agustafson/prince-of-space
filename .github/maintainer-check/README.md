# Maintainer check publisher

The `maintainer-approval` check is published by a dedicated GitHub App that belongs
to this repository's owner, not by an ordinary GitHub Actions job. This directory
documents why, how to set the app up, and how the workflow stays trusted.

- Workflow: [`.github/workflows/maintainer-approval.yml`](../workflows/maintainer-approval.yml)
- Predicate and publisher script: [`scripts/maintainer-check/`](../../scripts/maintainer-check/)
- Decision record: `docs/technical-decision-register.md` (TDR-031)

## Why a dedicated app

Every GitHub Actions workflow job runs under one shared identity: the `GitHub Actions`
GitHub App. A required check bound to that identity can be satisfied by any workflow
job with the same name — including a job added by an outside contributor's pull
request, which runs untrusted code but still reports checks. A passing, same-named
job from a contributor therefore proves nothing about maintainer review.

A repository ruleset can pin a required check to a specific app's identity
(`integration_id`). Once `maintainer-approval` is bound to the publisher app, only
a check run created through that app satisfies the requirement. A contributor job
named `maintainer-approval` still appears on the pull request, attributed to
`GitHub Actions`, and does not satisfy the bound check.

## One-time setup (repository owner)

The publisher app needs exactly one permission — **Checks: read and write** — and is
installed only on this repository. No webhooks, no events, no other permissions.

1. Open the prefilled registration form (checks write only, private to your account):
   <https://github.com/settings/apps/new?name=prince-of-space%20trusted%20publisher&description=Publishes%20the%20protected%20maintainer%20approval%20check%20for%20prince-of-space&url=https%3A%2F%2Fgithub.com%2Fagustafson%2Fprince-of-space&checks=write&public=false>
2. Click **Create GitHub App**, then under **Private keys** click **Generate a private
   key**; a `.pem` file downloads.
3. On the app's **Install App** page, install it on your account for **only this
   repository**.
4. In this repository: **Settings → Environments → New environment**, name it
   `maintainer-check`.
5. In that environment, add environment secret
   `MAINTAINER_CHECK_APP_PRIVATE_KEY` and paste the whole `.pem` contents.
6. In **Settings → Secrets and variables → Actions → Variables**, add repository
   variable `MAINTAINER_CHECK_APP_ID` with the numeric **App ID** from the app's
   General page.

Until both are set, the workflow logs a warning and publishes nothing (forks of this
repository stay quiet the same way).

### Rotating the key

Generate a new private key on the app's page, update the environment secret, then
delete the old key. No workflow change is needed.

## How the workflow stays trusted

- It triggers on `pull_request_target` and `pull_request_review`. Both run the
  workflow file from the **base branch**, so a pull request cannot modify the code
  that runs here.
- The predicate checkout is pinned to the pull request's **base sha**, never the
  merge ref, and nothing from the pull request is ever executed.
- The private key is an environment secret, visible only to jobs that reference the
  `maintainer-check` environment. Untrusted `pull_request` runs never see it.
- The workflow has no default token permissions; the job grants itself read-only
  `pull-requests` and `contents` access for metadata and the base checkout.
- The app token is minted per run, scoped to this repository, with `checks: write`
  only.
- The job is named `maintainer-approval-publisher` — deliberately different from the
  check name — so the Actions-created job check and the app-published
  `maintainer-approval` check never share a context.

## Policy

The predicate (`scripts/maintainer-check/predicate.mjs`) implements the accepted
maintainer policy, covered by unit tests (`node --test scripts/maintainer-check/`,
run by the `Maintainer check predicate` CI job):

- Maintainer-authored: the author is a non-bot `OWNER`, `MEMBER` or `COLLABORATOR`.
- Otherwise maintainer-approved: an `APPROVED` review by a non-bot maintainer; each
  reviewer's latest review decides (a later `CHANGES_REQUESTED` or `DISMISSED`
  overrides their approval).
- Bot accounts never count, as author or approver.
- Drafts publish `neutral` until marked ready.

Automation keeps working because its approvals come from real-user accounts:
`dependabot-auto-merge.yml` approves eligible dependency updates with the
PAT-backed `GH_ACTIONS_PUSH_TOKEN`, whose owner has a maintainer association.
Maintenance pull requests opened by workflows follow the same path — a real-user
approval satisfies the check.

## Binding the check in the ruleset (separate change)

When the maintainer policy becomes enforceable, add to
`.github/rulesets/main.json`:

```json
{
  "context": "maintainer-approval",
  "integration_id": <publisher app id>
}
```

…and apply it with `scripts/apply-main-ruleset.sh agustafson/prince-of-space` (see
`.github/rulesets/README.md`). The `integration_id` is what makes same-named
GitHub Actions jobs unable to satisfy the requirement; without it the binding
protects nothing. The publisher app's numeric id equals the value stored in
`MAINTAINER_CHECK_APP_ID`.

## Rerunning for one pull request

Maintainers can dispatch the workflow manually with a pull request number
(**Actions → Maintainer approval → Run workflow**) to republish the check, for
example after a dismissed review.
