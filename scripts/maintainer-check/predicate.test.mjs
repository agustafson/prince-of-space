import assert from 'node:assert/strict';
import { test } from 'node:test';

import { CHECK_NAME, MAINTAINERS, evaluateMaintainerApproval, flattenReviewPages } from './predicate.mjs';

const AGUSTAFSON = { id: 628811, login: 'agustafson', type: 'User' };
const BRIC3 = { id: 803621, login: 'bric3', type: 'User' };
const OUTSIDER = { id: 999999, login: 'outsider', type: 'User' };

function pr({ author = OUTSIDER, draft = false } = {}) {
  return {
    draft,
    user: author,
  };
}

function review({
  author,
  state = 'APPROVED',
  submittedAt = '2026-01-01T00:00:00Z',
}) {
  return {
    state,
    submitted_at: submittedAt,
    user: author,
  };
}

test('the published check name is stable', () => {
  assert.equal(CHECK_NAME, 'maintainer-approval');
});

test('the selected maintainers are agustafson and bric3 by stable id', () => {
  assert.deepEqual(
    MAINTAINERS.map((m) => m.login),
    ['agustafson', 'bric3'],
  );
  assert.equal(MAINTAINERS.find((m) => m.login === 'agustafson').id, 628811);
  assert.equal(MAINTAINERS.find((m) => m.login === 'bric3').id, 803621);
});

test('a pull request authored by a selected maintainer passes', () => {
  for (const maintainer of [AGUSTAFSON, BRIC3]) {
    const result = evaluateMaintainerApproval({
      pullRequest: pr({ author: maintainer }),
      reviews: [],
    });
    assert.equal(result.conclusion, 'success');
    assert.match(result.title, /Maintainer-authored/);
  }
});

test('an owner outside the selected maintainers does not pass', () => {
  const collaborator = { id: 777777, login: 'some-collaborator', type: 'User' };
  const authored = evaluateMaintainerApproval({
    pullRequest: pr({ author: collaborator }),
    reviews: [],
  });
  assert.equal(authored.conclusion, 'failure');

  const approvedByCollaborator = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [review({ author: collaborator })],
  });
  assert.equal(approvedByCollaborator.conclusion, 'failure');
});

test('an outside contributor without approval fails', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ author: OUTSIDER }),
    reviews: [],
  });
  assert.equal(result.conclusion, 'failure');
  assert.match(result.title, /Maintainer approval required/);
});

test('a selected maintainer approval makes a contributor pull request pass', () => {
  for (const maintainer of [AGUSTAFSON, BRIC3]) {
    const result = evaluateMaintainerApproval({
      pullRequest: pr({ author: OUTSIDER }),
      reviews: [review({ author: maintainer })],
    });
    assert.equal(result.conclusion, 'success');
    assert.match(result.title, /Maintainer-approved/);
  }
});

test('a bot approval never satisfies the policy', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ author: OUTSIDER }),
    reviews: [
      review({ author: { id: 1, login: 'coderabbitai[bot]', type: 'Bot' } }),
      review({ author: { id: 2, login: 'github-actions[bot]', type: 'Bot' } }),
    ],
  });
  assert.equal(result.conclusion, 'failure');
  assert.match(result.summary, /Approvals from other accounts or bots do not count/);
});

test('a bot-authored pull request needs a selected maintainer approval', () => {
  const dependabot = { id: 49699333, login: 'dependabot[bot]', type: 'Bot' };
  const unapproved = evaluateMaintainerApproval({
    pullRequest: pr({ author: dependabot }),
    reviews: [],
  });
  assert.equal(unapproved.conclusion, 'failure');

  const approved = evaluateMaintainerApproval({
    pullRequest: pr({ author: dependabot }),
    reviews: [review({ author: AGUSTAFSON })],
  });
  assert.equal(approved.conclusion, 'success');
  assert.match(approved.summary, /@agustafson/);
});

test('each reviewer decides by their latest review only', () => {
  const laterBlock = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, submittedAt: '2026-01-01T00:00:00Z' }),
      review({
        author: AGUSTAFSON,
        state: 'CHANGES_REQUESTED',
        submittedAt: '2026-01-02T00:00:00Z',
      }),
    ],
  });
  assert.equal(laterBlock.conclusion, 'failure');

  const dismissed = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: BRIC3, submittedAt: '2026-01-01T00:00:00Z' }),
      review({
        author: BRIC3,
        state: 'DISMISSED',
        submittedAt: '2026-01-02T00:00:00Z',
      }),
    ],
  });
  assert.equal(dismissed.conclusion, 'failure');
});

test('a later comment-only review preserves an earlier approval', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, submittedAt: '2026-01-01T00:00:00Z' }),
      review({ author: AGUSTAFSON, state: 'COMMENTED', submittedAt: '2026-01-02T00:00:00Z' }),
    ],
  });
  assert.equal(result.conclusion, 'success');
  assert.match(result.title, /Maintainer-approved/);
});

test('a comment-only review never changes a reviewer decision by itself', () => {
  const onlyComments = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [review({ author: BRIC3, state: 'COMMENTED' })],
  });
  assert.equal(onlyComments.conclusion, 'failure');

  const commentAfterBlock = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, state: 'CHANGES_REQUESTED', submittedAt: '2026-01-01T00:00:00Z' }),
      review({ author: AGUSTAFSON, state: 'COMMENTED', submittedAt: '2026-01-02T00:00:00Z' }),
    ],
  });
  assert.equal(commentAfterBlock.conclusion, 'failure');

  const approvalAfterComment = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, state: 'COMMENTED', submittedAt: '2026-01-01T00:00:00Z' }),
      review({ author: AGUSTAFSON, submittedAt: '2026-01-02T00:00:00Z' }),
    ],
  });
  assert.equal(approvalAfterComment.conclusion, 'success');
});

test('a dismissal still revokes an approval even with later comments', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, submittedAt: '2026-01-01T00:00:00Z' }),
      review({ author: AGUSTAFSON, state: 'DISMISSED', submittedAt: '2026-01-02T00:00:00Z' }),
      review({ author: AGUSTAFSON, state: 'COMMENTED', submittedAt: '2026-01-03T00:00:00Z' }),
    ],
  });
  assert.equal(result.conclusion, 'failure');
});

test('a second maintainer can still approve after another blocks', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, state: 'CHANGES_REQUESTED' }),
      review({ author: BRIC3 }),
    ],
  });
  assert.equal(result.conclusion, 'success');
});

test('review order in the input does not decide; submission time does', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [
      review({ author: AGUSTAFSON, state: 'DISMISSED', submittedAt: '2026-01-02T00:00:00Z' }),
      review({ author: AGUSTAFSON, submittedAt: '2026-01-01T00:00:00Z' }),
    ],
  });
  assert.equal(result.conclusion, 'failure');
});

test('draft pull requests report neutral', () => {
  const result = evaluateMaintainerApproval({
    pullRequest: pr({ author: AGUSTAFSON, draft: true }),
    reviews: [],
  });
  assert.equal(result.conclusion, 'neutral');
});

test('a maintainer id on a bot account does not pass', () => {
  const botLookalike = { id: 628811, login: 'agustafson[bot]', type: 'Bot' };
  const authored = evaluateMaintainerApproval({
    pullRequest: pr({ author: botLookalike }),
    reviews: [],
  });
  assert.equal(authored.conclusion, 'failure');

  const approvedByLookalike = evaluateMaintainerApproval({
    pullRequest: pr(),
    reviews: [review({ author: botLookalike })],
  });
  assert.equal(approvedByLookalike.conclusion, 'failure');
});

test('review pages are flattened before evaluation', () => {
  const pageOne = [review({ author: AGUSTAFSON, submittedAt: '2026-01-01T00:00:00Z' })];
  const pageTwo = [
    review({ author: AGUSTAFSON, state: 'DISMISSED', submittedAt: '2026-01-02T00:00:00Z' }),
    review({ author: BRIC3, submittedAt: '2026-01-03T00:00:00Z' }),
  ];
  const flattened = flattenReviewPages([pageOne, pageTwo]);
  assert.equal(flattened.length, 3);
  assert.equal(
    evaluateMaintainerApproval({ pullRequest: pr(), reviews: flattened }).conclusion,
    'success',
  );
});

test('flattenReviewPages accepts plain and empty review lists', () => {
  const plain = [review({ author: OUTSIDER })];
  assert.equal(flattenReviewPages(plain).length, 1);
  assert.deepEqual(flattenReviewPages([]), []);
  assert.deepEqual(flattenReviewPages([[]]), []);
  assert.deepEqual(flattenReviewPages(null), []);
});
