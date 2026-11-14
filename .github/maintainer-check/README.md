# Maintainer check publisher

The `maintainer-approval` check is published by a dedicated GitHub App that belongs
to this repository's owner, not by an ordinary GitHub Actions job. This directory
documents why, how to set the app up, and how the workflow stays trusted.

- Trusted publisher: [`.github/workflows/maintainer-approval.yml`](../workflows/maintainer-approval.yml)
- Unprivileged collector: [`.github/workflows/maintainer-approval-collector.yml`](../workflows/maintainer-approval-collector.yml)
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
   `maintainer-check`. Under **Deployment branches and tags** choose **Selected
   branches and tags** and add the pattern `main`. This is the credential boundary:
   see below.
5. In that environment, add environment secret
   `MAINTAINER_CHECK_APP_PRIVATE_KEY` and paste the whole `.pem` contents.
6. In **Settings → Secrets and variables → Actions → Variables**, add repository
   variable `MAINTAINER_CHECK_APP_ID` with the numeric **App ID** from the app's
   General page.

Until the variable is set, the workflows log a warning and publish nothing (forks of
this repository stay quiet the same way).

### Rotating the key

Generate a new private key on the app's page, update the environment secret, then
delete the old key. No workflow change is needed.

## How the publisher stays trusted

Two workflows cooperate; only the second is privileged.

**The collector is deliberately unprivileged.** `pull_request` and
`pull_request_review` run the pull request's *own* workflow version in the
merge-ref context — so a pull request can change what the collector runs — and pull
requests from forks or Dependabot receive no secrets at all. The collector therefore
holds no permissions and no secrets: it only writes the pull request number into an
artifact of its own run.

**The publisher runs only from the default branch.** It is triggered by
`workflow_run` after the collector completes, which always uses the default-branch
workflow file and can access secrets even when the triggering run could not; by a
scheduled reconciliation sweep (below); and by `workflow_dispatch`, which requires
write access and, off the default branch, is rejected by the environment's
deployment policy before any step runs. A pull request cannot change what the
publisher runs. Resolution and publication are separate jobs: the resolve job holds
no environment and no secrets and only turns the event into a list of pull request
numbers; the publish job is the only privileged step in the chain.

Within the publisher:

- **Executable scripts come only from the trusted publisher revision.** The
  checkout is pinned to `github.sha` — the default-branch commit the publisher
  workflow itself runs from. A pull request's base branch is never trusted
  (contributors can create branches containing modified scripts), so no pull
  request revision is ever executed here.
- **The collector's artifact is treated as hostile.** It is extracted into an
  isolated temporary directory, never the workspace, so no artifact-supplied file
  (for example a `.git` directory) can reach the trusted checkout or its Git
  metadata. Only the pull request number is read, validated as an integer, and all
  pull request and review data is fetched from the API.
- **Review pages are fetched with pagination** (`--paginate --slurp`), so no
  approval or later change-requested review can be hidden beyond the first page; a
  failed page fails the run.
- **Evaluations are serialized per pull request.** Every entry path — collector
  events, dispatch reruns and the scheduled sweep — funnels into one shared
  publication job keyed by the pull request number, so evaluations of the same
  pull request queue behind each other whatever path started them, and an older
  evaluation cannot publish over a newer revocation.
- The workflow has no default token permissions; the job grants itself read-only
  `pull-requests`, `contents` and `actions` access for metadata, the artifact and
  the trusted checkout.
- The app token is minted per run, scoped to this repository, with `checks: write`
  only.

**A failed or modified collector cannot leave a stale success standing.** A pull
request can make its own collector fail, stay silent on a dismissal, or never run
at all. The publisher therefore also reconciles on a schedule from the default
branch: it lists every open pull request — with no silent truncation, so no pull
request is starved; if more than 250 are open, beyond what one matrix can carry,
the sweep is marked unsuccessful instead of dropping targets — and re-publishes
each check, with each pull request isolated in its own job so one failure never
skips the others and any failed read or publication marks the sweep unsuccessful.
A pull request whose review evidence cannot be read gets a fail-closed failure
result for its known head, never silence; if even the pull request details cannot
be read, the head sha the trusted listing already supplied is invalidated the
same way.

Schedules carry no deadline. GitHub may delay or drop scheduled runs, and in a
project like this one it disables schedules after 60 days without repository
activity, so a withdrawn approval takes effect on the next collector event or on
the next successful sweep — never by a fixed time. A disabled schedule is
re-enabled explicitly: enable the Maintainer approval workflow again in the
repository's Actions tab (or through the documented cron-edit route) — ordinary
repository activity does not reliably re-enable it. A maintainer can refresh one
pull request at any moment with a manual dispatch.

**The environment is the credential boundary for the key.** Environment secrets are
available to *any* job that references the environment — including a
`pull_request`-triggered job a same-repository contributor adds — unless the
environment's deployment branch policy excludes that ref. `pull_request` runs use
`refs/pull/N/merge`, which matches no allowed pattern, so the policy in setup step 4
keeps the key away from contributor-controlled workflow jobs. Restrict it to
`main` (the publisher's only ref) and verify it after setup: a pull request job
that references `environment: maintainer-check` must fail to deploy.

**The jobs are named `maintainer-approval-resolve` and
`maintainer-approval-publisher`** — deliberately different from the check name —
so the Actions-created job checks and the app-published `maintainer-approval`
check never share a context.

Until the publisher workflow is merged to the default branch, the `workflow_run`
chain and the schedule cannot run (GitHub only triggers them when the workflow file
exists on the default branch); before that, only the `workflow_dispatch` path is
exercisable, and only from refs the environment's deployment policy allows.

## Policy

The predicate (`scripts/maintainer-check/predicate.mjs`) implements the accepted
maintainer policy, covered by unit tests (`node --test scripts/maintainer-check/`,
run by the `Maintainer check predicate` CI job):

- Maintainer-authored: the author is one of the selected maintainer accounts,
  `agustafson` and `bric3`, matched by their stable numeric GitHub user ids.
- Otherwise maintainer-approved: an `APPROVED` review by one of those accounts;
  each reviewer's latest deciding review governs — a later `CHANGES_REQUESTED` or
  `DISMISSED` overrides their approval, while comment-only (`COMMENTED`) reviews
  never change a reviewer's decision, so a follow-up comment preserves an
  existing approval.
- Bot accounts never count, as author or approver.
- Drafts publish `neutral` until marked ready.

Automation keeps working because its approvals come from a selected maintainer
account: `dependabot-auto-merge.yml` approves eligible dependency updates with the
PAT-backed `GH_ACTIONS_PUSH_TOKEN`, owned by `agustafson`. Maintenance pull requests
opened by workflows follow the same path — an approval from a selected maintainer
account satisfies the check.

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
example after a dismissed review. The dispatch must run from a ref the
`maintainer-check` environment's deployment policy allows — normally `main`.
