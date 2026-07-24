#!/usr/bin/env bash
# compare.sh — one-shot A/B of the local (pom.xml) jar vs the global sai jar.
#
# Runs BOTH the payload-level prefix-stability probe and (optionally) the live
# cache hit-rate test, side by side, so you can see the caching delta.
#
# Usage:
#   ./compare.sh [model]
#     model (optional) enables the live end-to-end test too,
#     e.g. ./compare.sh copilot/claude-haiku-4.5
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

LOCAL_JAR="$HERE/../target/sai-1.0-SNAPSHOT.jar"
GLOBAL_JAR="$HOME/.local/share/sai/sai.jar"
MODEL="${1:-}"

ver() { unzip -p "$1" 'META-INF/maven/com.phonepe.sentinel-ai/sentinel-ai-core/pom.properties' 2>/dev/null | sed -n 's/^version=//p'; }

echo "=================================================================="
echo " sentinel-ai cache A/B"
echo "   local  (pom.xml): $(ver "$LOCAL_JAR")   $LOCAL_JAR"
echo "   global (sai cmd): $(ver "$GLOBAL_JAR")   $GLOBAL_JAR"
echo "=================================================================="

echo; echo "### PREFIX STABILITY — global jar"
"$HERE/run_capture.sh" "$GLOBAL_JAR" 3 8793
echo; echo "### PREFIX STABILITY — local jar"
"$HERE/run_capture.sh" "$LOCAL_JAR" 3 8794

if [[ -n "$MODEL" ]]; then
  echo; echo "### LIVE HIT-RATE — global jar"
  "$HERE/live_cache.sh" "$GLOBAL_JAR" "$MODEL" 5
  echo; echo "### LIVE HIT-RATE — local jar"
  "$HERE/live_cache.sh" "$LOCAL_JAR" "$MODEL" 5
else
  echo; echo "(pass a model arg to also run the live hit-rate test, e.g. ./compare.sh copilot/claude-haiku-4.5)"
fi

echo; echo "Higher shared-prefix % == more cache-friendly. Local should win."
