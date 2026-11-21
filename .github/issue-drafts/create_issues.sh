#!/usr/bin/env bash
# Creates one GitHub issue per draft file in this folder (in order).
# Usage (needs the GitHub CLI logged in with write access):
#   bash .github/issue-drafts/create_issues.sh
set -euo pipefail
REPO="${REPO:-Sandali0726/Campus-wifi-vs-cellular}"
cd "$(dirname "$0")"
for f in [0-9][0-9]-*.md; do
  title="$(head -n1 "$f" | sed 's/^# //')"
  tail -n +3 "$f" > /tmp/issue_body.md
  gh issue create --repo "$REPO" --title "$title" --body-file /tmp/issue_body.md
done
