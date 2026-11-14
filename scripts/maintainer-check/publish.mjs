#!/usr/bin/env node
// Publishes the protected `maintainer-approval` check run through the dedicated
// publisher GitHub App. Only .github/workflows/maintainer-approval.yml runs this
// script, with the trusted publisher revision's copies of both files — the
// default-branch commit the publisher workflow runs from; see
// .github/maintainer-check/README.md for the trust model.
import { readFile } from 'node:fs/promises';

import { CHECK_NAME, evaluateMaintainerApproval, flattenReviewPages } from './predicate.mjs';

const repository = process.env.GITHUB_REPOSITORY;
const token = process.env.APP_TOKEN;
const prPath = process.env.PR_JSON;
const reviewsPath = process.env.REVIEWS_JSON;

if (!repository || !token || !prPath || !reviewsPath) {
  console.error('Missing required environment: GITHUB_REPOSITORY, APP_TOKEN, PR_JSON, REVIEWS_JSON.');
  process.exit(2);
}

const pullRequest = JSON.parse(await readFile(prPath, 'utf8'));
// When any review page could not be read, no approval can be verified: publish
// a fail-closed failure for the known head instead of trusting partial data.
const result =
  process.env.REVIEWS_UNREADABLE === 'true'
    ? {
        conclusion: 'failure',
        title: 'Review evidence unavailable',
        summary:
          'Not every review page could be read, so no maintainer approval can be verified. ' +
          'This result is fail-closed and is refreshed by the next evaluation.',
      }
    : evaluateMaintainerApproval({
        pullRequest,
        reviews: flattenReviewPages(JSON.parse(await readFile(reviewsPath, 'utf8'))),
      });

const runUrl =
  process.env.GITHUB_SERVER_URL && process.env.GITHUB_RUN_ID
    ? `${process.env.GITHUB_SERVER_URL}/${repository}/actions/runs/${process.env.GITHUB_RUN_ID}`
    : undefined;

const response = await fetch(`https://api.github.com/repos/${repository}/check-runs`, {
  method: 'POST',
  headers: {
    Accept: 'application/vnd.github+json',
    Authorization: `Bearer ${token}`,
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'prince-of-space-maintainer-check',
  },
  body: JSON.stringify({
    name: CHECK_NAME,
    head_sha: pullRequest.head.sha,
    status: 'completed',
    conclusion: result.conclusion,
    output: { title: result.title, summary: result.summary },
    ...(runUrl ? { details_url: runUrl } : {}),
  }),
});

if (!response.ok) {
  console.error(`Check run creation failed: ${response.status} ${await response.text()}`);
  process.exit(1);
}

const check = await response.json();
console.log(
  `Published '${CHECK_NAME}' with conclusion ${result.conclusion} on ${pullRequest.head.sha} (check run ${check.id}).`,
);
