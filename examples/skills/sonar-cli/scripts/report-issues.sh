#!/usr/bin/env bash
# report-issues.sh — Fetch open issues from SonarQube for the current Git branch
# and print a Markdown summary to stdout.
#
# Usage:
#   SONAR_TOKEN=<token> bash report-issues.sh \
#     --host http://localhost:9000 \
#     --project-key my-project \
#     [--branch <branch>]        # defaults to current Git branch
#     [--max-issues 50]          # top N issues to show (default: 20)

set -euo pipefail

HOST=""
PROJECT_KEY=""
BRANCH=""
MAX_ISSUES=20

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)          HOST="$2";         shift 2 ;;
    --project-key)   PROJECT_KEY="$2";  shift 2 ;;
    --branch)        BRANCH="$2";       shift 2 ;;
    --max-issues)    MAX_ISSUES="$2";   shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$HOST" || -z "$PROJECT_KEY" ]]; then
  echo "Error: --host and --project-key are required." >&2
  exit 1
fi

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "Error: SONAR_TOKEN environment variable is not set." >&2
  exit 1
fi

# Detect branch if not provided
if [[ -z "$BRANCH" ]]; then
  BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  if [[ -z "$BRANCH" ]]; then
    echo "Warning: could not detect Git branch; fetching issues without branch filter." >&2
  fi
fi

BRANCH_PARAM=""
if [[ -n "$BRANCH" ]]; then
  BRANCH_PARAM="&branch=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$BRANCH")"
fi

echo "# SonarQube Issue Report"
echo ""
echo "**Project:** \`${PROJECT_KEY}\`"
echo "**Branch:**  \`${BRANCH:-<default>}\`"
echo "**Server:**  ${HOST}"
echo ""

# ── Severity counts ──────────────────────────────────────────────────────────
echo "## Summary by Severity"
echo ""
echo "| Severity | Count |"
echo "|----------|-------|"

for SEV in BLOCKER CRITICAL MAJOR MINOR INFO; do
  COUNT=$(curl -s -u "${SONAR_TOKEN}:" \
    "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}${BRANCH_PARAM}&resolved=false&severities=${SEV}&ps=1" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('total', 0))")
  printf "| %-8s | %5s |\n" "$SEV" "$COUNT"
done

echo ""

# ── Quality Gate ─────────────────────────────────────────────────────────────
QG_STATUS=$(curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/qualitygates/project_status?projectKey=${PROJECT_KEY}${BRANCH_PARAM//&branch=/\&branch=}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('projectStatus',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")

echo "## Quality Gate"
echo ""
echo "Status: **${QG_STATUS}**"
echo ""

# ── Top Issues ────────────────────────────────────────────────────────────────
echo "## Top ${MAX_ISSUES} Open Issues (by Severity)"
echo ""
echo "| # | Severity | Type | Rule | File | Line | Message |"
echo "|---|----------|------|------|------|------|---------|"

curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}${BRANCH_PARAM}&resolved=false&ps=${MAX_ISSUES}&s=SEVERITY&asc=false" \
  | python3 - << 'PYEOF'
import sys, json

data = json.load(sys.stdin)
issues = data.get("issues", [])
for i, issue in enumerate(issues, 1):
    component = issue.get("component", "")
    # Strip project prefix from component path
    file_path = component.split(":", 1)[-1] if ":" in component else component
    # Truncate long paths
    if len(file_path) > 40:
        file_path = "..." + file_path[-37:]
    line = issue.get("line", "—")
    rule = issue.get("rule", "")
    severity = issue.get("severity", "")
    itype = issue.get("type", "")
    message = issue.get("message", "").replace("|", "\\|")
    if len(message) > 60:
        message = message[:57] + "..."
    print(f"| {i} | {severity} | {itype} | `{rule}` | `{file_path}` | {line} | {message} |")
PYEOF

echo ""
echo "---"
echo "_Full details: ${HOST}/project/issues?id=${PROJECT_KEY}$([ -n "$BRANCH" ] && echo "&branch=${BRANCH}" || echo "")_"
