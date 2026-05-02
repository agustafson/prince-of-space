#!/usr/bin/env bash
# Usage: git bisect run ./scripts/bisect-eval-idempotency.sh
# Requires PRINCE_EVAL_ROOTS (guava + spring-framework checkouts). Exits:
#   0 = eval harness passed (0 parse errors, 0 idempotency failures)
#   1 = regression / failure
#   125 = skip (PRINCE_EVAL_ROOTS unset, or compile failed — see stderr)
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 125

if [[ -z "${PRINCE_EVAL_ROOTS:-}" ]]; then
  echo "Set PRINCE_EVAL_ROOTS to comma-separated Java project roots (guava, spring-framework)." >&2
  exit 125
fi

if ! ./gradlew :core:compileJava :core:compileTestJava -q; then
  echo "SKIP: compile failed" >&2
  exit 125
fi

export PRINCE_EVAL_LINE_LENGTH="${PRINCE_EVAL_LINE_LENGTH:-120}"
export PRINCE_EVAL_WRAP_STYLE="${PRINCE_EVAL_WRAP_STYLE:-BALANCED}"
export PRINCE_EVAL_REPORT_DIR="${PRINCE_EVAL_REPORT_DIR:-/tmp/prince-bisect-eval}"

set +e
# --rerun-tasks forces a fresh eval each bisect step (eval harness reads env at execution time).
./gradlew :core:evalTest --rerun-tasks -q
st=$?
set -e
if [[ "$st" -eq 0 ]]; then
  echo "GOOD: evalTest passed" >&2
  exit 0
fi
echo "BAD: evalTest failed (exit $st)" >&2
exit 1
