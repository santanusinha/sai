# cache-bench

Small scripts to test the efficacy of the sentinel-ai prompt-caching changes
(local `pom.xml` version vs the version baked into the globally installed `sai`).

Two independent signals:

1. **Prefix stability** (provider-independent) — how many leading bytes of the
   prompt stay byte-identical across runs. Prompt caches key on a stable prefix,
   so more shared prefix = more cacheable. This is the reliable signal.
2. **Live hit-rate** (provider-dependent) — actual `cached_tokens` reported by
   the provider. Only meaningful if your provider returns
   `prompt_tokens_details.cached_tokens` (Copilot/some proxies report 0).

## Files
- `capture_proxy.py`   — fake OpenAI endpoint that records request payloads
- `prefix_stability.py`— runs a jar N times, prints shared-prefix %
- `run_capture.sh`     — sources `~/.config/sai/.env`, then runs the above
- `live_cache.sh`      — real end-to-end cached-token hit-rate for one jar
- `compare.sh`         — A/B: local pom jar vs global sai jar

## Quick start
```bash
cd cache-bench

# Payload-level A/B (no network, works regardless of provider):
./compare.sh

# Also include real hit-rate against a provider:
./compare.sh copilot/claude-haiku-4.5
```

## Individual use
```bash
# just the local jar
./run_capture.sh ../target/sai-1.0-SNAPSHOT.jar 3

# just the global jar
./run_capture.sh ~/.local/share/sai/sai.jar 3

# live hit-rate for one jar
./live_cache.sh ../target/sai-1.0-SNAPSHOT.jar copilot/claude-haiku-4.5 5
```

Interpretation: the more cache-friendly build shows a **higher shared-prefix %**
and, on a provider that reports it, a **higher cached hit-rate** after the first
(cold) request.
