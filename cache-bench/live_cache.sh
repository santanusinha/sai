#!/usr/bin/env bash
# live_cache.sh — real end-to-end cache hit-rate for one jar.
#
# Fires the SAME prompt N times at your configured provider, then reads the
# persisted session stats (requestTokens / cachedTokens) sai wrote to disk.
# Works with any provider; hit-rate is only meaningful if the provider returns
# prompt_tokens_details.cached_tokens (OpenAI/Anthropic-native do; some proxies
# and Copilot may report 0).
#
# Usage:
#   ./live_cache.sh <jar> <model> [runs]
#
# Example:
#   ./live_cache.sh ../target/sai-1.0-SNAPSHOT.jar copilot/claude-haiku-4.5 5
set -euo pipefail
JAR="${1:?usage: live_cache.sh <jar> <model> [runs]}"
MODEL="${2:?need model, e.g. copilot/claude-haiku-4.5}"
RUNS="${3:-5}"
PROMPT="Reply with exactly one word: ping. Do not use any tools."
SESS="$HOME/.local/state/sai/sessions"

SAI_ENV="$HOME/.config/sai/.env"
if [[ -f "$SAI_ENV" ]]; then set -a; source "$SAI_ENV"; set +a; fi

read_stats() {  # $1 = session dir -> "req cached"
  python3 - "$1" <<'PY'
import json, os, sys
d = sys.argv[1]; req = cac = 0
try:
    for line in open(os.path.join(d, "messages.jsonl")):
        line = line.strip()
        if not line: continue
        st = json.loads(line).get("stats")
        if isinstance(st, dict) and st.get("requestTokens"):
            req = st["requestTokens"]
            cac = st.get("requestTokenDetails", {}).get("cachedTokens", 0)
except Exception:
    pass
print(req, cac)
PY
}

echo "jar=$JAR  model=$MODEL  runs=$RUNS"
tot_req=0; tot_cac=0
for i in $(seq 1 "$RUNS"); do
  echo "$PROMPT" | java -jar "$JAR" --headless --model "$MODEL" >/dev/null 2>&1
  sleep 2
  newest="$(ls -dt "$SESS"/*/ 2>/dev/null | head -1)"
  read -r req cac <<<"$(read_stats "$newest")"
  rate=0; [[ "$req" -gt 0 ]] && rate=$(python3 -c "print(f'{$cac*100.0/$req:.1f}')")
  printf "  run %d: req=%-6s cached=%-6s hit=%s%%\n" "$i" "$req" "$cac" "$rate"
  tot_req=$((tot_req+req)); tot_cac=$((tot_cac+cac))
done
avg=0; [[ "$tot_req" -gt 0 ]] && avg=$(python3 -c "print(f'{$tot_cac*100.0/$tot_req:.1f}')")
echo "  ---------------------------------------------"
echo "  overall: req=$tot_req cached=$tot_cac hit=$avg%"
