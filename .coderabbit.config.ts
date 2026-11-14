import { defineConfig } from "@coderabbitai/config";

const showcaseInstructions = `
These are formatter inputs and expected results, not application code.
Review changed output against docs/canonical-formatting-rules.md.
Look for lost or moved comments, changed annotation targets or multiplicity,
changed literal values, unstable layout, and unexplained output changes.
Do not request unrelated style changes, cleanup, or production-code refactors
inside showcase inputs or expected outputs. Regeneration alone is not proof
that a changed expected result is correct.
`;

const integrationInstructions = `
Keep configuration defaults and supported options aligned with FormatterConfig.
Preserve the public API, CLI exit codes, UTF-8 and stdin/stdout contracts.
Check cancellation, subprocess cleanup, framing and error paths where relevant.
A failed format must not replace a document with partial output or diagnostics.
Normal integrations must not enable benchmark-only single-pass formatting.
`;

export default defineConfig({
  language: "en-US",
  inheritance: false,
  early_access: false,
  tone_instructions:
    "Be direct, respectful, and specific. Explain the concrete failure and cite the relevant rule or code. Avoid praise, style debates, and speculative refactoring.",
  reviews: {
    profile: "chill",
    auto_approvals: {
      enabled: false,
      allow_author_approval: false,
    },
    auto_review: {
      enabled: true,
      auto_incremental_review: true,
      auto_pause_after_reviewed_commits: 0,
      drafts: false,
      base_branches: [],
    },
    review_progress: true,
    commit_status: true,
    fail_commit_status: false,
    review_status: true,
    review_details: true,
    high_level_summary: true,
    high_level_summary_in_walkthrough: true,
    collapse_walkthrough: true,
    poem: false,
    in_progress_fortune: false,
    sequence_diagrams: false,
    suggested_labels: false,
    auto_apply_labels: false,
    auto_assign_reviewers: false,
    assess_linked_issues: true,
    path_filters: [
      "!examples/compare.html",
      "!examples/external/outputs/**",
    ],
    path_instructions: [
      {
        path: "**",
        instructions: `
Prioritize concrete correctness, compatibility, security and performance bugs.
For formatter output, docs/canonical-formatting-rules.md is normative and wins
when other prose disagrees. Consult current, non-superseded decisions in
docs/technical-decision-register.md and the contributing and architecture docs.
Do not substitute another formatter's style for this project's contract.
Report cross-file omissions when behavior, tests, showroom results and docs
no longer agree. Do not claim checks ran without actual check results.
Avoid repeating an existing finding or deterministic CI diagnostic unless
additional reasoning identifies a different defect or necessary correction.
`,
      },
      {
        path: "modules/core/src/main/**",
        instructions: `
Preserve Java program meaning, comment placement intent, annotation targets
and multiplicity, literal values, control-character escaping and text-block
whitespace semantics. Successful normal formatting must reach a fixed point:
format(format(x)) == format(x). Keep convergence failures distinguishable
from parse/configuration failures; do not hide diagnostics to accept invalid
Java. The documented benchmark-only fast mode is the intentional exception.
Preserve Java 17 runtime/API compatibility; accepted source language levels
are separate from the JDK used to build. Keep third-party types out of public
API signatures and Formatter imports. Internal classes stay package-private
unless cross-package access genuinely requires otherwise. Preserve JSpecify
contracts. Review wrapping against the canonical rules, including exceptions,
not a hard assumption that every line must fit the target width.
`,
      },
      {
        path: "modules/core/src/test/**",
        instructions: `
Require a regression that exposes the actual bug and verifies intended output.
For changed formatter behavior, look for fixed-point and semantic-preservation
coverage appropriate to that change. Parser acceptance or a regenerated golden
alone does not prove preservation. Respect deliberately malformed fixtures.
Tests for package-private implementation belong in the matching package.
Do not weaken assertions or change expected files merely to accommodate a bug.
`,
      },
      ...["examples/inputs/**", "examples/outputs/**"].map((path) => ({
        path,
        instructions: showcaseInstructions,
      })),
      ...[
        "modules/cli/**",
        "modules/spotless/**",
        "modules/intellij-plugin/**",
        "modules/vscode-extension/**",
        "modules/core-bundled/**",
      ].map((path) => ({ path, instructions: integrationInstructions })),
      ...[
        "**/*.gradle.kts",
        "gradle/libs.versions.toml",
        ".github/workflows/**",
      ].map((path) => ({
        path,
        instructions: `
Check dependency compatibility, artifact/shading boundaries, least-privilege
workflow permissions and safe handling of untrusted fork metadata and code.
For Gradle dependencies, keep coordinates and pinned versions in the version catalog.
Distinguish the JDK needed by build tools from the Java 17 published target.
Do not expose secrets or execute untrusted pull request code in privileged jobs.
Keep VS Code compilation and packaging checks: Gradle alone does not cover them.
`,
      })),
      {
        path: "docs/**",
        instructions: `
Check consistency with the implemented formatter and canonical rules.
Intentional normative changes need corresponding tests and applicable showroom
updates; policy or public-option changes need a decision-register entry.
Do not treat historical implementation plans or superseded decisions as current
requirements. Review documentation for correctness, not stylistic rewrites.
`,
      },
    ],
    pre_merge_checks: {
      title: { mode: "off" },
      description: { mode: "off" },
      issue_assessment: { mode: "off" },
      custom_checks: [],
    },
    finishing_touches: {
      unit_tests: { enabled: false },
      simplify: { enabled: false },
      autofix: { enabled: false },
      fix_ci: { enabled: false },
      resolve_merge_conflict: { enabled: false },
      custom: [],
    },
    tools: {
      "github-checks": { enabled: true },
      pmd: { enabled: false },
    },
  },
  chat: {
    auto_reply: false,
    art: false,
    allow_non_org_members: true,
  },
  knowledge_base: {
    learnings: { scope: "local" },
    code_guidelines: {
      enabled: true,
      filePatterns: [
        ".claude/CLAUDE.md",
        "docs/contributing.md",
        "docs/architecture.md",
        "docs/canonical-formatting-rules.md",
        "docs/formatting-rules.md",
        "docs/technical-decision-register.md",
      ],
    },
  },
});
