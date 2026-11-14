// Maintainer-approval policy predicate for the protected check publisher.
//
// Only the trusted workflow (.github/workflows/maintainer-approval.yml) runs this
// file, and it always runs the base-revision copy from the target branch — never
// a version from a pull request. See .github/maintainer-check/README.md for the
// trust model and docs/technical-decision-register.md (TDR-031) for the decision.
//
// Policy:
//   * A pull request passes when its author is a maintainer: a non-bot account
//     whose author_association is OWNER, MEMBER or COLLABORATOR
//     ("maintainer-authored").
//   * Otherwise it passes once a maintainer has approved it: an APPROVED review
//     by a non-bot account with a maintainer author_association. Each reviewer's
//     chronologically latest review decides; a later CHANGES_REQUESTED or
//     DISMISSED review overrides that reviewer's earlier approval.
//   * Bot accounts (user.type === "Bot", which covers GitHub App bots such as
//     dependabot[bot], coderabbitai[bot] and github-actions[bot]) never satisfy
//     the policy, as author or as approver. Automated dependency and maintenance
//     pull requests therefore pass through their real-user approval (the
//     PAT-backed account behind GH_ACTIONS_PUSH_TOKEN has a maintainer
//     association), preserving the accepted automation exceptions.
//   * Draft pull requests report `neutral` until they are marked ready.
//
// The predicate is pure: it reads only the documents passed in and returns a
// conclusion plus title and summary text for the check run.

export const CHECK_NAME = 'maintainer-approval';

const MAINTAINER_ASSOCIATIONS = new Set(['OWNER', 'MEMBER', 'COLLABORATOR']);

function isBot(user) {
  return user?.type === 'Bot';
}

function isMaintainerAssociation(association) {
  return MAINTAINER_ASSOCIATIONS.has(association);
}

function latestReviewPerReviewer(reviews) {
  const latest = new Map();
  const ordered = [...reviews].sort((a, b) =>
    String(a.submitted_at ?? '').localeCompare(String(b.submitted_at ?? '')),
  );
  for (const review of ordered) {
    const login = review.user?.login;
    if (login) {
      latest.set(login, review);
    }
  }
  return latest;
}

export function evaluateMaintainerApproval({ pullRequest, reviews = [] }) {
  const authorLogin = pullRequest?.user?.login ?? 'unknown';
  const authorAssociation = pullRequest?.author_association ?? 'NONE';
  const authorIsBot = isBot(pullRequest?.user);

  if (pullRequest?.draft) {
    return {
      conclusion: 'neutral',
      title: 'Draft pull request',
      summary:
        'This pull request is a draft. The maintainer check will be published once it is marked ready for review.',
    };
  }

  if (!authorIsBot && isMaintainerAssociation(authorAssociation)) {
    return {
      conclusion: 'success',
      title: 'Maintainer-authored',
      summary: `@${authorLogin} authored this pull request (association: ${authorAssociation}).`,
    };
  }

  const approvals = [];
  const notCounted = [];
  for (const review of latestReviewPerReviewer(reviews).values()) {
    if (review.state !== 'APPROVED') {
      continue;
    }
    const login = review.user?.login ?? 'unknown';
    if (isBot(review.user) || !isMaintainerAssociation(review.author_association ?? 'NONE')) {
      notCounted.push(login);
      continue;
    }
    approvals.push(login);
  }

  if (approvals.length > 0) {
    return {
      conclusion: 'success',
      title: 'Maintainer-approved',
      summary: `Approved by maintainer ${approvals.map((login) => `@${login}`).join(', ')} (each reviewer's latest review decides).`,
    };
  }

  const summaryParts = [
    `Authored by @${authorLogin} (association: ${authorAssociation}${authorIsBot ? ', bot account' : ''}); a maintainer approval is required.`,
  ];
  if (notCounted.length > 0) {
    summaryParts.push(
      `Bot or non-maintainer approvals do not count: ${notCounted.map((login) => `@${login}`).join(', ')}.`,
    );
  }
  return {
    conclusion: 'failure',
    title: 'Maintainer approval required',
    summary: summaryParts.join(' '),
  };
}
