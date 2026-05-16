#!/usr/bin/env bash
# wait-for-task.sh — Poll the SonarQube Compute Engine until a task finishes.
#
# Usage:
#   SONAR_TOKEN=<token> bash wait-for-task.sh \
#     --host http://localhost:9000 \
#     --task-id AXr9abc123 \
#     [--timeout 300] [--interval 5]

set -euo pipefail

HOST=""
TASK_ID=""
TIMEOUT=300
INTERVAL=5

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)     HOST="$2";    shift 2 ;;
    --task-id)  TASK_ID="$2"; shift 2 ;;
    --timeout)  TIMEOUT="$2"; shift 2 ;;
    --interval) INTERVAL="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$HOST" || -z "$TASK_ID" ]]; then
  echo "Error: --host and --task-id are required." >&2
  exit 1
fi

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "Error: SONAR_TOKEN environment variable is not set." >&2
  exit 1
fi

ELAPSED=0
echo "Waiting for Sonar task ${TASK_ID} to complete (timeout: ${TIMEOUT}s)..."

while true; do
  STATUS=$(curl -s -u "${SONAR_TOKEN}:" \
    "${HOST}/api/ce/task?id=${TASK_ID}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['task']['status'])")

  echo "  [${ELAPSED}s] Status: ${STATUS}"

  case "$STATUS" in
    SUCCESS)
      echo "Task completed successfully."
      exit 0
      ;;
    FAILED|CANCELLED)
      echo "Task ended with status: ${STATUS}" >&2
      exit 2
      ;;
  esac

  if [[ "$ELAPSED" -ge "$TIMEOUT" ]]; then
    echo "Timeout waiting for task ${TASK_ID} after ${TIMEOUT}s." >&2
    exit 3
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done
