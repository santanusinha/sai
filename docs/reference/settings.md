# Settings Configuration (`settings.yaml`)

SAI supports a hierarchical settings file — `settings.yaml` — that defines LLM providers, per-model tuning, and per-mode overrides in a single file. This is the **primary** configuration mechanism; environment variables serve as a **fallback** when no config entry exists.

## Overview

`settings.yaml` lives in your config directory (`~/.config/sai/settings.yaml`) and organises settings as a three-level hierarchy:

```
provider
  └── model
        └── mode
```

Each level can carry a `tuning` block. Resolution is a **bottom-up merge** — mode settings override model settings, which override provider-level defaults. The merge uses **rhs-wins-if-non-null** semantics: if a field is set at a higher level, it overrides the lower level; if not, the lower level's value applies.

| Level | Keyed by | Owns |
|-------|----------|------|
| **Provider** | name (`openai`, `azure`, `openrouter`, …) | `type`, `endpoint`, `apiKey`, `apiVersion` (Azure), `organizationId`, `projectId`, `extraHeaders`, `tuning` (provider defaults), `models` |
| **Model** | `modelId` (nested inside provider) | `tuning` (model overrides), `defaultMode`, `modes` |
| **Mode** | name (`coding`, `planning`, …) (nested inside model) | `tuning` (sparse overrides) |

!!! tip "When to use settings.yaml vs environment variables"
    Use `settings.yaml` when you need multiple providers configured simultaneously, per-model tuning, or modes. Use environment variables (`.env`) for simple single-provider setups — they continue to work as a fallback.

---

## Provider Types

| `type` | Builder | Fields consumed | Notes |
|--------|---------|-----------------|-------|
| `openai` | `SimpleOpenAI.builder()` | `endpoint` → `baseUrl`, `apiKey`, `organizationId`, `projectId`, `extraHeaders` | Covers OpenAI, OpenRouter, Together AI, Groq, vLLM, and any OpenAI-compatible endpoint. `copilot-proxy` also maps here. |
| `azure` | `SimpleOpenAIAzure.builder()` | all `openai` fields **+** `apiVersion` | Azure OpenAI Service only. |
| `copilot` | `CopilotDirectProvider` (built-in) | GitHub token at `~/.config/sai/copilot_token` | **Not config-driven.** Always available as `copilot/<model>`. Cannot be overridden via `settings.yaml`. |

!!! info "Built-in Copilot provider"
    The `copilot` provider is always available — it does **not** need to be listed in `settings.yaml`. It uses GitHub OAuth token exchange internally and cannot be expressed as a `type: openai` config entry. If you mistakenly list `copilot` in `settings.yaml`, it is **ignored** with a warning.

---

## File Format

```yaml
# ~/.config/sai/settings.yaml
# Secrets via ${ENV} interpolation; never commit keys.

providers:
  openai:
    type: openai
    endpoint: ${OPENAI_ENDPOINT:-https://api.openai.com/v1}
    apiKey: ${OPENAI_API_KEY}
    organizationId: ${OPENAI_ORGANIZATION:-}
    projectId: ${OPENAI_PROJECT_ID:-}
    extraHeaders:
      Helicone-Auth: "Bearer ${HELICONE_KEY}"
    # Provider-level defaults — apply to ALL models under this provider
    tuning:
      temperature: 0.2
      encodingType: O200K_BASE

    models:
      gpt-4o:
        # Model-level settings — override provider defaults
        tuning:
          temperature: 0.3
          topP: 0.9
          contextWindowSize: 128000
        modes:
          coding:
            tuning:
              temperature: 0.2
              toolChoice: AUTO
          planning:
            tuning:
              temperature: 0.8
              reasoning: HIGH

  openrouter:
    type: openai
    endpoint: https://openrouter.ai/api/v1
    apiKey: ${OPENROUTER_API_KEY}
    extraHeaders:
      HTTP-Referer: "https://sai.local"
      X-Title: "SAI"
    tuning:
      temperature: 0.7

    models:
      "anthropic/claude-3.5-sonnet":
        tuning:
          temperature: 0.7
          topP: 0.9
          encodingType: CL100K_BASE
          contextWindowSize: 200000
        modes:
          coding:
            tuning:
              toolChoice: AUTO

  azure:
    type: azure
    endpoint: ${AZURE_ENDPOINT}
    apiKey: ${AZURE_API_KEY}
    apiVersion: ${AZURE_API_VERSION:-2024-10-21}

  copilot-proxy:
    type: openai
    endpoint: https://models.github.ai/inference
    apiKey: ${COPILOT_PROXY_TOKEN:-dummy}
    extraHeaders:
      Accept: "application/vnd.github+json"
      X-GitHub-Api-Version: "2022-11-28"

# copilot is NOT listed here — it is always available as a built-in provider.
```

---

## Field Reference

### Provider Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | Yes | Provider type: `openai` or `azure` |
| `endpoint` | String | Yes | API endpoint / base URL |
| `apiKey` | String | Yes | API key (typically `${ENV}` reference) |
| `apiVersion` | String | Azure only | Azure API version (default: `2024-10-21`) |
| `organizationId` | String | No | OpenAI organization ID |
| `projectId` | String | No | OpenAI project ID |
| `extraHeaders` | Map\<String, String\> | No | Extra HTTP headers injected into every request |
| `tuning` | ModelTuning | No | Provider-level tuning defaults (apply to all models) |
| `models` | Map\<String, ModelEntry\> | No | Models defined under this provider |

### Model Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tuning` | ModelTuning | No | Model-level tuning (overrides provider defaults) |
| `defaultMode` | String | No | Default mode name (used when no mode is specified) |
| `modes` | Map\<String, ModeEntry\> | No | Modes defined under this model |

### Mode Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tuning` | ModelTuning | No | Mode-level tuning overrides (merged on top of model settings) |

---

## Tuning Fields (`ModelTuning`)

The `tuning` block is shared between `settings.yaml` and persona files. All fields are optional — only set what you need to override.

| Field | Type | Description |
|-------|------|-------------|
| `maxTokens` | Integer | Maximum tokens to generate |
| `temperature` | Float | Sampling temperature (0.0–2.0) |
| `topP` | Float | Nucleus sampling threshold (0.0–1.0) |
| `timeout` | Duration | Request timeout (e.g. `PT30S` for 30 seconds) |
| `parallelToolCalls` | Boolean | Whether to allow parallel tool calls |
| `seed` | Integer | Random seed for reproducibility |
| `presencePenalty` | Float | Presence penalty (-2.0 to 2.0) |
| `frequencyPenalty` | Float | Frequency penalty (-2.0 to 2.0) |
| `logitBias` | Map\<String, Integer\> | Token bias map |
| `reasoning` | String | Reasoning effort: `LOW`, `MEDIUM`, `HIGH` |
| `encodingType` | String | Tokenizer encoding: `CL100K_BASE`, `O200K_BASE`, etc. |
| `contextWindowSize` | Integer | Context window size in tokens |
| `toolChoice` | String | Tool choice: `AUTO` or `REQUIRED` |
| `extraArgs` | Map\<String, String\> | Free-form passthrough arguments |

!!! note "Merge semantics"
    - **Scalar fields** (temperature, topP, etc.): rhs wins if non-null.
    - **encodingType / contextWindowSize**: rhs wins if non-null (handled directly, not through `ModelAttributes.merge` which uses sentinel-based semantics).
    - **Maps** (`logitBias`, `extraArgs`): rhs map replaces lhs map entirely if non-null.

---

## Mode Defaults

- If a model defines **exactly one** mode, that mode is automatically the **default** — it is applied even when the model string does not specify a mode (e.g. `copilot/claude-sonnet-4.6` applies the sole mode's settings).
- If a model defines **multiple** modes, specify one explicitly via `provider/model/mode`, or set `defaultMode` on the model.
- If a model defines **zero** modes, no mode-level override is applied — just the model-level and provider-level settings.

---

## Model String Format

The model string is `provider/model[/mode]`:

```
copilot/claude-sonnet-4.6                    → provider=copilot, model=claude-sonnet-4.6, mode=(default or none)
copilot/claude-sonnet-4.6/coding             → provider=copilot, model=claude-sonnet-4.6, mode=coding
openrouter/anthropic/claude-3.5-sonnet       → provider=openrouter, model=anthropic/claude-3.5-sonnet, mode=(default or none)
openrouter/anthropic/claude-3.5-sonnet/plan  → provider=openrouter, model=anthropic/claude-3.5-sonnet, mode=plan
```

Splitting uses `/` with **limit 3**:

- `parts[0]` → provider
- `parts[1]` → model (may itself contain `/` for providers like OpenRouter)
- `parts[2]` → mode (optional)

!!! warning "Model IDs with slashes"
    Some providers (OpenRouter) use model IDs like `anthropic/claude-3.5-sonnet`. The `limit=3` split handles this: `openrouter/anthropic/claude-3.5-sonnet/planning` splits into `["openrouter", "anthropic/claude-3.5-sonnet", "planning"]`.

---

## Resolution / Precedence

Effective settings are built by **bottom-up merge** through the hierarchy, then the persona tuning is applied on top:

```
provider-level defaults (ModelTuning)
   ⊕ model-level settings  (ModelTuning.merge: provider ⊕ model)
   ⊕ mode-level overrides  (ModelTuning.merge: above ⊕ mode)
   ⊕ persona tuning        (ModelTuning.merge: above ⊕ personaTuning)
```

Where `⊕` denotes `ModelTuning.merge(lhs, rhs)` — **rhs wins if non-null**.

### Fallback behavior

- **No `settings.yaml` file**: env-var fallback is used for provider configuration (backward compatible).
- **No entry for the requested model**: a warning is logged and the persona's inline tuning is used directly.
- **No persona tuning either**: framework defaults from `AgentFactory` apply (context window `128_000`, `EncodingType.O200K_BASE`).

### AgentFactory invariants

`AgentFactory.createAgent` applies its invariants **after** the resolver output:

- `.withParallelToolCalls(false)` — always forced
- `defaultModelSettings()` — context window `128_000`, `EncodingType.O200K_BASE` as floor

The resolver output feeds these invariants; they remain the floor/ceiling.

---

## `${ENV}` Interpolation

`settings.yaml` supports environment variable interpolation using two syntaxes:

| Syntax | Description | Example |
|--------|-------------|---------|
| `${VAR}` | Replaced with the env var value. If unset, the placeholder text is preserved. | `${OPENAI_API_KEY}` |
| `${VAR:-default}` | Replaced with the env var value, or `default` if unset. | `${OPENAI_ENDPOINT:-https://api.openai.com/v1}` |

!!! danger "Never commit secrets"
    Always use `${ENV}` references for API keys. Never put literal keys in `settings.yaml`. Add `settings.yaml` to `.gitignore` if it contains sensitive references.

---

## Using `tuning` in Personas

Personas can use the same `ModelTuning` class for inline settings. The `tuning` field is preferred over the legacy `modelSettings` / `modelOptions` fields:

```yaml
# examples/personas/coder.yaml
agentId: coder
name: Coder
description: A coding-focused AI agent
model: copilot/claude-sonnet-4.6/coding
prompt: |
  You are an expert coder...

# Preferred: unified tuning section
tuning:
  temperature: 0.5
  toolChoice: AUTO

# Legacy (still works, but tuning takes precedence if both present):
# modelSettings:
#   temperature: 0.5
# modelOptions:
#   toolChoice: AUTO
```

If both `tuning` and legacy `modelSettings` / `modelOptions` are present in a persona, `tuning` takes precedence.

---

## Backward Compatibility

### No config file → env-var fallback

If `settings.yaml` is **absent**, the factory uses the current env-var reads (`OPENAI_*`, `AZURE_*`, `COPILOT_PROXY_*`). Existing `.env` setups keep working without any config file.

### Config > env

If a provider is defined in **both** `settings.yaml` and environment variables, the config entry wins. This is intentional — config is the primary source.

### Legacy persona fields

Personas with `modelSettings` / `modelOptions` (legacy) continue to work. The `tuning` field is preferred but both are supported.

### Built-in providers

`copilot` is always available as a built-in provider, regardless of `settings.yaml`. The `copilot-proxy` provider can optionally be listed in `settings.yaml` (as `type: openai` with the GitHub inference endpoint), but if not listed, falls back to the `COPILOT_PROXY_ENDPOINT` env var.

---

## Complete Example

```yaml
# ~/.config/sai/settings.yaml

providers:
  # OpenAI with provider-level defaults
  openai:
    type: openai
    endpoint: ${OPENAI_ENDPOINT:-https://api.openai.com/v1}
    apiKey: ${OPENAI_API_KEY}
    organizationId: ${OPENAI_ORGANIZATION:-}
    tuning:
      temperature: 0.2
      encodingType: O200K_BASE
    models:
      gpt-4o:
        tuning:
          temperature: 0.3
          topP: 0.9
          contextWindowSize: 128000
        modes:
          coding:
            tuning:
              temperature: 0.2
              toolChoice: AUTO
          planning:
            tuning:
              temperature: 0.8
              reasoning: HIGH

  # OpenRouter — multiple models with slash IDs
  openrouter:
    type: openai
    endpoint: https://openrouter.ai/api/v1
    apiKey: ${OPENROUTER_API_KEY}
    extraHeaders:
      HTTP-Referer: "https://sai.local"
      X-Title: "SAI"
    tuning:
      temperature: 0.7
    models:
      "anthropic/claude-3.5-sonnet":
        tuning:
          temperature: 0.7
          topP: 0.9
          encodingType: CL100K_BASE
          contextWindowSize: 200000
        modes:
          coding:
            tuning:
              toolChoice: AUTO

  # Azure OpenAI
  azure:
    type: azure
    endpoint: ${AZURE_ENDPOINT}
    apiKey: ${AZURE_API_KEY}
    apiVersion: ${AZURE_API_VERSION:-2024-10-21}

  # Groq — fast inference
  groq:
    type: openai
    endpoint: https://api.groq.com/openai/v1
    apiKey: ${GROQ_API_KEY}

  # Copilot proxy (optional — falls back to COPILOT_PROXY_ENDPOINT env var if absent)
  copilot-proxy:
    type: openai
    endpoint: https://models.github.ai/inference
    apiKey: ${COPILOT_PROXY_TOKEN:-dummy}
    extraHeaders:
      Accept: "application/vnd.github+json"
      X-GitHub-Api-Version: "2022-11-28"

# copilot is NOT listed — it is always available as a built-in provider.
```

---

## See Also

- [Environment Variables](environment.md) - Env var reference (fallback when no config entry exists)
- [Persona Format](persona-format.md) - Persona configuration including the `tuning` field
- [CLI Options](cli-options.md) - `--model` and `--config-dir` flags
- [Configuration Guide](../getting-started/configuration.md) - Provider setup walkthrough
