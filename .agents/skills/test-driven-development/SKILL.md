---
name: test-driven-development
description: >-
  Drives feature work with tests first (red–green–refactor), minimal production
  code, and fast feedback. Use when implementing or changing behavior, fixing
  bugs with regression tests, or when the user asks for TDD, test-first, or
  failing tests before implementation.
---

# Test-Driven Development

## When to apply

- New behavior or API surface.
- Bug fixes (regression test first).
- Refactors that must preserve behavior.

Skip strict TDD only when the change is purely mechanical (rename, move) with existing coverage, or when the user explicitly asks otherwise.

## Core loop

1. **Red** — Add one **failing** test that expresses desired behavior (assertion or compilation error for missing API is acceptable only at the very first step).
2. **Green** — Write the **smallest** production change that makes the test pass. No extra features.
3. **Refactor** — Clean up with **all tests still green**. Repeat the loop for the next behavior slice.

## Rules

- **One logical change per cycle** where practical; avoid a large batch of new tests with no implementation.
- **Test names** describe behavior (`formats_chained_calls_with_balanced_folding`), not implementation details.
- **Prefer fast unit tests**; add slower or integration tests when boundaries require them.
- **After green**, run the **full** test suite (or the project’s agreed scope) before moving on.
- **Regression**: for a bug, commit or keep a test that fails on the old code and passes after the fix.

## Java / Gradle (this repo)

- Prefer tests next to or under the module’s conventional test source set (`src/test/java`).
- Use **golden-file** or snapshot-style assertions when output text must stay stable (formatter output, error messages).
- For determinism-sensitive formatter work, tests should pin **inputs and config** (including `languageLevel`, line endings); align behavior with `docs/canonical-formatting-rules.md`.

## Agent checklist

- [ ] Failing test exists before (or defines) new production behavior.
- [ ] Implementation is minimal to satisfy that test.
- [ ] Refactor does not drop coverage of the new behavior.
- [ ] All relevant tests pass before finishing the task.
