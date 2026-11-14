// Maintainer-approval policy predicate for the protected check publisher.
//
// Only the trusted publisher (.github/workflows/maintainer-approval.yml) runs this
// file, and it always runs the trusted publisher revision's copy — the
// default-branch commit the publisher workflow runs from — never a version from
// a pull request or its base branch. See .github/maintainer-check/README.md for
// the trust model and docs/technical-decision-register.md (TDR-031) for the
// decision.
//
// Policy:
//   * A pull request passes when its author is a selected maintainer: one of the
//     accounts listed in MAINTAINERS below, matched by its stable numeric GitHub
//     user id ("maintainer-authored").
//   * Otherwise it passes once a selected maintainer has approved it: an APPROVED
//     review by one of those accounts. Each reviewer's chronologically latest
//     deciding review governs; a later CHANGES_REQUESTED or DISMISSED review
//     overrides that reviewer's earlier approval, while comment-only
//     (COMMENTED) reviews never change a reviewer's decision, so a follow-up
//     comment preserves an existing approval (the captain's selected rule).
//   * Bot accounts (user.type === "Bot", which covers GitHub App bots such as
//     dependabot[bot], coderabbitai[bot] and github-actions[bot]) never satisfy
//     the policy, as author or as approver. Automated dependency and maintenance
//     pull requests therefore pass through their approval by a selected
//     maintainer account: the PAT behind GH_ACTIONS_PUSH_TOKEN belongs to
//     agustafson, preserving the accepted automation exceptions.
//   * Draft pull requests report `neutral` until they are marked ready.
//
// The predicate is pure: it reads only the documents passed in and returns a
// conclusion plus title and summary text for the check run.

export const CHECK_NAME = 'maintainer-approval';

// The maintainers selected for this policy (PROS-5). Matched by numeric user id,
// which survives account renames; logins are kept for readability.
export const MAINTAINERS = [
  { id: 628811, login: 'agustafson' },
  { id: 803621, login: 'bric3' },
];

const MAINTAINER_IDS = new Set(MAINTAINERS.map((m) => m.id));

function isBot(user) {
  return user?.type === 'Bot';
}

function isMaintainer(user) {
  return Boolean(user) && !isBot(user) && MAINTAINER_IDS.has(user.id);
}

function maintainerList() {
  return MAINTAINERS.map((m) => `@${m.login}`).join(' and ');
}

// The trusted workflow fetches review pages with `gh api --paginate --slurp`,
// which returns an array of page arrays; a plain array is accepted too.
export function flattenReviewPages(rawReviews) {
  if (!Array.isArray(rawReviews)) {
    return [];
  }
  if (rawReviews.length > 0 && Array.isArray(rawReviews[0])) {
    return rawReviews.flat();
  }
  return rawReviews;
}

function latestDecidingReviewPerReviewer(reviews) {
  const latest = new Map();
  const ordered = [...reviews].sort((a, b) =>
    String(a.submitted_at ?? '').localeCompare(String(b.submitted_at ?? '')),
  );
  for (const review of ordered) {
    // Comment-only reviews never change a reviewer's decision: skipping them
    // keeps an earlier approval alive through later comments while a later
    // CHANGES_REQUESTED or DISMISSED review still overrides it.
    if (review.state === 'COMMENTED') {
      continue;
    }
    const login = review.user?.login;
    if (login) {
      latest.set(login, review);
    }
  }
  return latest;
}

export function evaluateMaintainerApproval({ pullRequest, reviews = [] }) {
  const author = pullRequest?.user;
  const authorLogin = author?.login ?? 'unknown';

  if (pullRequest?.draft) {
    return {
      conclusion: 'neutral',
      title: 'Draft pull request',
      summary:
        'This pull request is a draft. The maintainer check will be published once it is marked ready for review.',
    };
  }

  if (isMaintainer(author)) {
    return {
      conclusion: 'success',
      title: 'Maintainer-authored',
      summary: `@${authorLogin} authored this pull request.`,
    };
  }

  const approvals = [];
  const notCounted = [];
  for (const review of latestDecidingReviewPerReviewer(reviews).values()) {
    if (review.state !== 'APPROVED') {
      continue;
    }
    const login = review.user?.login ?? 'unknown';
    if (!isMaintainer(review.user)) {
      notCounted.push(login);
      continue;
    }
    approvals.push(login);
  }

  if (approvals.length > 0) {
    return {
      conclusion: 'success',
      title: 'Maintainer-approved',
      summary: `Approved by maintainer ${approvals.map((login) => `@${login}`).join(', ')} (each reviewer's latest deciding review governs; comment-only reviews do not change it).`,
    };
  }

  const summaryParts = [
    `Authored by @${authorLogin}${isBot(author) ? ' (bot account)' : ''}; the policy requires authorship or approval by ${maintainerList()}.`,
  ];
  if (notCounted.length > 0) {
    summaryParts.push(
      `Approvals from other accounts or bots do not count: ${notCounted.map((login) => `@${login}`).join(', ')}.`,
    );
  }
  return {
    conclusion: 'failure',
    title: 'Maintainer approval required',
    summary: summaryParts.join(' '),
  };
}
