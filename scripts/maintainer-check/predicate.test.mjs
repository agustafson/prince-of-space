import assert from 'node:assert/strict';
import { test } from 'node:test';

import { CHECK_NAME, evaluateMaintainerApproval, flattenReviewPages } from './predicate.mjs';

function pr({ login = 'contributor', type = 'User', association = 'NONE', draft = false } = {}) {
  return {
    draft,
    author_association: association,
    user: { login, type },
  };
}

function review({
  login,
  type = 'User',
  association = 'NONE',
  state = 'APPROVED',
  submittedAt = '2026-01-01T00:00:00Z',
}) {
  return {
    state,
    submitted_at: submittedAt,
    author_association: association,
    user: { login, type },
  };
}

test('the published check name is stable', () => {
  assert.equal(CHECK_NAME, 'maintainer-approval');
});

test('a maintainer-authored pull request passes', () => {
  for (const association of ['OWNER', 'MEMBER', 'COLLABORATOR']) {
    const result = evaluateMaintainerApproval({
      pullRequest: pr({ login: 'maintainer', association }),
      reviews: [],
    });
    assert.equal(result.conclusion, 'success');
    assert.match(result.title, /Maintainer-authored/);
  }
});

test('an outside contributor without approval fails', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'newcomer', association: 'FIRST_TIME_CONTRIBUTOR' }),
    reviews: [],
  });
  assert.equal(result.conclusion, 'failure');
  assert.match(result.title, /Maintainer approval required/);
});

test('a maintainer approval makes a contributor pull request pass', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'newcomer' }),
    reviews: [review({ login: 'maintainer', association: 'OWNER' })],
  });
  assert.equal(result.conclusion, 'success');
  assert.match(result.title, /Maintainer-approved/);
});

test('a bot approval never satisfies the policy', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'newcomer' }),
    reviews: [
      review({ login: 'coderabbitai[bot]', type: 'Bot', association: 'CONTRIBUTOR' }),
      review({ login: 'github-actions[bot]', type: 'Bot', association: 'NONE' }),
    ],
  });
  assert.equal(result.conclusion, 'failure');
  assert.match(result.summary, /Bot or non-maintainer approvals do not count/);
});

test('a bot-authored pull request needs a maintainer approval', () => {
  const dependabot = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'dependabot[bot]', type: 'Bot', association: 'CONTRIBUTOR' }),
    reviews: [],
  });
  assert.equal(dependabot.conclusion, 'failure');

  const approved = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'dependabot[bot]', type: 'Bot', association: 'CONTRIBUTOR' }),
    reviews: [review({ login: 'agustafson', association: 'OWNER' })],
  });
  assert.equal(approved.conclusion, 'success');
  assert.match(approved.summary, /@agustafson/);
});

test('each reviewer decides by their latest review only', () => {
  const laterBlock = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ login: 'maintainer', association: 'OWNER', submittedAt: '2026-01-01T00:00:00Z' }),
      review({
        login: 'maintainer',
        association: 'OWNER',
        state: 'CHANGES_REQUESTED',
        submittedAt: '2026-01-02T00:00:00Z',
      }),
    ],
  });
  assert.equal(laterBlock.conclusion, 'failure');

  const dismissed = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ login: 'maintainer', association: 'OWNER', submittedAt: '2026-01-01T00:00:00Z' }),
      review({
        login: 'maintainer',
        association: 'OWNER',
        state: 'DISMISSED',
        submittedAt: '2026-01-02T00:00:00Z',
      }),
    ],
  });
  assert.equal(dismissed.conclusion, 'failure');
});

test('a second reviewer can still approve after another reviewer blocks', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ login: 'strict', association: 'OWNER', state: 'CHANGES_REQUESTED' }),
      review({ login: 'kind', association: 'COLLABORATOR' }),
    ],
  });
  assert.equal(result.conclusion, 'success');
});

test('review order in the input does not decide; submission time does', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ login: 'maintainer', association: 'OWNER', submittedAt: '2026-01-02T00:00:00Z', state: 'DISMISSED' }),
      review({ login: 'maintainer', association: 'OWNER', submittedAt: '2026-01-01T00:00:00Z' }),
    ],
  });
  assert.equal(result.conclusion, 'failure');
});

test('draft pull requests report neutral', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ login: 'maintainer', association: 'OWNER', draft: true }),
    reviews: [],
  });
  assert.equal(result.conclusion, 'neutral');
});

test('a bot maintainer-lookalike association does not pass', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [review({ login: 'someone[bot]', type: 'Bot', association: 'OWNER' })],
  });
  assert.equal(result.conclusion, 'failure');
});

test('review pages are flattened before evaluation', () => {
  const pageOne = [review({ login: 'maintainer', association: 'OWNER', submittedAt: '2026-01-01T00:00:00Z' })];
  const pageTwo = [
    review({ login: 'maintainer', association: 'OWNER', state: 'DISMISSED', submittedAt: '2026-01-02T00:00:00Z' }),
    review({ login: 'other', association: 'COLLABORATOR', submittedAt: '2026-01-03T00:00:00Z' }),
  ];
  const flattened = flattenReviewPages([pageOne, pageTwo]);
  assert.equal(flattened.length, 3);
  assert.equal(
    evaluateMaintainerApproval({ pullRequest: pr(), reviews: flattened }).conclusion,
    'success',
  );
});

test('flattenReviewPages accepts plain and empty review lists', () => {
  const plain = [review({ login: 'x' })];
  assert.equal(flattenReviewPages(plain).length, 1);
  assert.deepEqual(flattenReviewPages([]), []);
  assert.deepEqual(flattenReviewPages([[]]), []);
  assert.deepEqual(flattenReviewPages(null), []);
});
