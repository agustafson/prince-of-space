#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s OWNER/REPOSITORY\n' "${0##*/}" >&2
  exit 2
}

[[ $# -eq 1 ]] || usage
repository=$1

if [[ ! "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  printf 'Repository must be OWNER/REPOSITORY: %s\n' "$repository" >&2
  exit 2
fi

if ! command -v gh >/dev/null 2>&1; then
  printf 'GitHub CLI (gh) is required.\n' >&2
  exit 127
fi

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ruleset_file="$repo_root/.github/rulesets/main.json"

ruleset_id="$(
  gh api "repos/${repository}/rulesets?includes_parents=false" \
    --jq '[.[] | select(.name == "main")] | if length == 1 then .[0].id else error("expected exactly one repository ruleset named main") end'
)"

if [[ ! "$ruleset_id" =~ ^[0-9]+$ ]]; then
  printf 'Could not resolve repository ruleset "main" in %s.\n' "$repository" >&2
  exit 1
fi

printf 'Updating "main" ruleset in %s (id %s).\n' "$repository" "$ruleset_id"
gh api --method PUT "repos/${repository}/rulesets/${ruleset_id}" --input "$ruleset_file"
