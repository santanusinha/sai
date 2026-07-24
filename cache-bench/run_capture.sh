#!/usr/bin/env bash
# run_capture.sh — source sai's env, then run prefix_stability.py for a jar.
#
# Usage:
#   ./run_capture.sh <jar> [runs] [port]
#
# Example:
#   ./run_capture.sh ../target/sai-1.0-SNAPSHOT.jar 3 8791
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
JAR="${1:?usage: run_capture.sh <jar> [runs] [port]}"
RUNS="${2:-3}"
PORT="${3:-8791}"

SAI_ENV="$HOME/.config/sai/.env"
if [[ -f "$SAI_ENV" ]]; then set -a; source "$SAI_ENV"; set +a; fi

python3 "$HERE/prefix_stability.py" "$JAR" "$RUNS" "$PORT"
