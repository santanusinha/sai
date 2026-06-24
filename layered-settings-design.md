# Layered Settings — Design Proposal

| | |
|---|---|
| **Status** | Draft v2 — answers incorporated, awaiting review |
| **Scope** | SAI only (no Sentinel-AI framework changes) |
| **Author** | Sai Coder |
| **Branch** | `master` |
| **Stage** | Design only — **nothing implemented yet** |

> Goal: split model **tuning** out of personas into a **hierarchical** structure —
> **provider → model → mode** — so `/model` automatically picks up the right
> per-model settings and "modes" (coding/planning/…) can override on top.
> Providers become **config-driven** (opencode, openrouter, …) instead of
> env-var-only, while **Copilot stays special-cased** due to its OAuth token
> exchange. All claims below were verified against the current source (see
> [§11 Verification](#11-verification-notes-checked-against-source)).

---

## 1. Problem recap (confirmed in the code)

### 1.1 Tuning conflation

- `AgentConfig` (the `@Jacksonized` persona) conflates **identity + prompt +
  skills/tools** with **model selection** (`model` string) and **tuning**
  (`modelSettings`, `modelOptions`). So per-model tuning is **duplicated across
  persona files**:
  - `examples/personas/coder.yaml` → `temperature: 0.5`
  - `examples/personas/kimi-coder.yaml` → `temperature: 0.6`, `topP: 0.95`,
    `presencePenalty: 0`, `modelOptions.toolChoice: AUTO`
- **The pain point**: `SlashCommandContext.rebuildAgent()` for
  `/model provider/model` splits the string and calls
  `agentFactory.createAgent(modelName, currentAgentConfig.get())` — i.e. it swaps
  **only the model string** and **reuses the current persona's
  `AgentConfig`** (and therefore its `modelSettings` / `modelOptions`). The new
  model inherits the old model's temperature / topP / tokenizer. There is **no
  "mode" concept** at all.
- The upstream stack is merge-friendly: `ModelSettings.merge(lhs, rhs)`,
  `ModelAttributes.merge(lhs, rhs)`, and `SimpleOpenAIModelOptions.merge(other)`
  all implement **"rhs wins if non-null"** — a ready foundation for layered
  overrides.

### 1.2 Provider configuration is env-var-only and hardcoded

- `ConfigurableProviderFactory` has a `switch` on exactly four provider names:
  `azure`, `openai`, `copilot`, `copilot-proxy`. Any other provider string throws
  `IllegalArgumentException("Unsupported provider: …")`.
- All credentials are read from **environment variables** via `EnvLoader` inside
  the factory methods:
  - `azureModel()` → `AZURE_ENDPOINT`, `AZURE_API_KEY`, `AZURE_API_VERSION`
  - `openAIModel()` → `OPENAI_API_KEY`, `OPENAI_ENDPOINT`, `OPENAI_ORGANIZATION`,
    `OPENAI_PROJECT_ID`, `OPENAI_EXTRA_HEADERS`
  - `copilotProxyModel()` → `COPILOT_PROXY_ENDPOINT`
  - `copilotDirectProvider` → reads GitHub token from
    `~/.config/sai/copilot_token` (file-based, not env)
- Adding a new OpenAI-compatible provider (OpenRouter, OpenCode, Together AI,
  Groq, …) currently requires:
  1. Setting `OPENAI_ENDPOINT` to the provider's base URL, and
  2. Setting `OPENAI_API_KEY` to the provider's key.
  This works but means **only one OpenAI-compatible provider can be active at a
  time** — you cannot have `openai/gpt-4o` and `openrouter/anthropic/claude-3.5`
  configured simultaneously, because both would share the same `OPENAI_*` env
  vars.
- `SaiCommand` constructs the factory at line 241:
  `new ConfigurableProviderFactory(provider, mapper, okHttpClient)` — the
  provider name comes from the `--model provider/model` CLI split. The factory is
  **single-provider**: it holds one `provider` string and creates one kind of
  `ChatCompletionServices` for its lifetime.
- `AgentFactory.resolveProviderFactory()` creates a **new**
  `ConfigurableProviderFactory` only when `requestTransforms` are present (to
  inject an interceptor into the OkHttpClient); otherwise it reuses the shared
  factory.

### 1.3 Copilot is irreducibly special

`CopilotDirectProvider` performs a multi-step OAuth flow that **cannot be
reduced to a `type: openai` config entry**:

1. Reads the GitHub OAuth token from `~/.config/sai/copilot_token`.
2. Exchanges it for a short-lived Copilot bearer token via
   `GET https://api.github.com/copilot_internal/v2/token`.
3. Caches the bearer token at `~/.config/sai/copilot_access_token.json`.
4. Schedules automatic refresh 60 s before expiry.
5. On every `get()` call, builds a `SimpleOpenAI` with two interceptors:
   - A URL-rewrite interceptor that strips the `/v1` prefix (Copilot's chat
     endpoint is `/chat/completions`, not `/v1/chat/completions`).
   - A header interceptor that injects all Copilot-required headers
     (`editor-version`, `copilot-integration-id`, `openai-intent`, …) and
     replaces the `Authorization` header with the current bearer token. On 401
     or 404, it refreshes the token inline and retries once.

This is **stateful, time-sensitive, and protocol-specific** — it cannot be
expressed as `type: openai` with a different `baseUrl` + `apiKey`.

The `copilot-proxy` provider (`GithubCopilot` class) is different: it simply
extends `OpenAIProvider` with a custom base URL (`https://models.github.ai/
inference`) and three static headers (`Accept`, `Content-Type`,
`X-GitHub-Api-Version`). This **can** be expressed as a config-driven
`type: openai` provider with `extraHeaders`.

---

## 2. Goals / Non-goals

**Goals**

- Split tuning into a **hierarchical** structure: **provider → model → mode**.
- Make `/model` automatically pick up the *right* per-model settings (the bug
  fix).
- Add **modes** (e.g. `coding`, `planning`) that override settings on top of the
  active model — scoped per-model, not global.
- Reuse the existing `merge()` layering instead of inventing new resolution
  logic.
- **Make providers config-driven**: define arbitrary providers (opencode,
  openrouter, together, groq, …) in a single `settings.yaml` with `type`,
  `endpoint`, `apiKey`, and optional fields — replacing the hardcoded env-var
  switch.
- **Keep Copilot special-cased**: `copilot` remains a built-in provider handled
  by `CopilotDirectProvider`; it is **not** config-driven.
- Stay backward-compatible with today's personas and env-var setup.
- **Reuse the same config classes** for model/mode settings in both
  `settings.yaml` and persona files.

**Non-goals**

- No changes to the Sentinel-AI framework.
- Personas are **not** removed — they keep identity, prompt, skills, tools, and a
  *default* model + mode reference.
- No implementation as part of this document.
- No change to the Copilot OAuth flow itself (`CopilotDirectProvider` stays as
  is).

---

## 3. The hierarchical structure

### 3.1 Overview

Settings are organised as a **three-level hierarchy** in a single
`settings.yaml` file:

```
provider
  └── model
        └── mode
```

Each level can carry `modelSettings`, `modelOptions`, and `extraArgs`. Resolution
is a **bottom-up recursive merge**: mode settings override model settings, which
override provider-level defaults. If a level provides nothing, the parent's
values apply as-is.

| Level | Keyed by | Owns | Today's source |
|-------|----------|------|----------------|
| **Provider** | name (`openai`, `azure`, `copilot`, `openrouter`, `opencode`, …) | `type` (`openai` \| `azure`), `endpoint`, `apiKey`, `apiVersion` (azure only), `organizationId`, `projectId`, `extraHeaders`, `proxyEndpoint` **+ optional provider-default `modelSettings` / `modelOptions` / `extraArgs`** | env vars in `ConfigurableProviderFactory` (to be replaced by `settings.yaml`) |
| **Model** | `modelId` (nested inside provider) | `modelSettings`, `modelOptions`, `extraArgs` **+ optional model-default settings** | duplicated inline in personas |
| **Mode** | name (`coding`, `planning`, …) (nested inside model) | **sparse overrides** of `modelSettings`, `modelOptions`, `extraArgs` | does not exist yet |

### 3.2 Provider types

| `type` | Builder used | Fields consumed | Notes |
|--------|-------------|-----------------|-------|
| `openai` | `SimpleOpenAI.builder()` | `endpoint` → `baseUrl`, `apiKey`, `organizationId`, `projectId`, `extraHeaders` (→ OkHttp interceptor), `retryConfig`, `objectMapper`, `clientAdapter` | Covers OpenAI, OpenRouter, OpenCode, Together, Groq, vLLM, any OpenAI-compatible endpoint. `copilot-proxy` (GithubCopilot) also maps here with `extraHeaders`. |
| `azure` | `SimpleOpenAIAzure.builder()` | all `openai` fields **+** `apiVersion` | Azure OpenAI Service only. |
| `copilot` | `CopilotDirectProvider` (built-in) | GitHub token file at `~/.config/sai/copilot_token` | **Not config-driven.** Always available as `copilot/<model>`. Cannot be overridden via `settings.yaml`. |

> **Key insight**: `SimpleOpenAI` and `SimpleOpenAIAzure` already expose
> `baseUrl`, `apiKey`, `organizationId`, `projectId`, `clientAdapter`,
> `retryConfig`, and `objectMapper` on their builders. `SimpleOpenAIAzure` adds
> `apiVersion`. Any OpenAI-compatible provider is just `type: openai` with a
> different `baseUrl` + `apiKey` — no new code paths needed.

### 3.3 The `ModelTuning` class (shared between settings.yaml and personas)

A single reusable class — `ModelTuning` — carries the tuning fields at every
level (provider, model, mode). This class is used in:

- `settings.yaml` — provider-level defaults, model-level settings, mode-level
  overrides
- Persona files — the `modelSettings` / `modelOptions` section is replaced by a
  `tuning` section of the same type

```java
@Value
@Builder
@Jacksonized
public class ModelTuning {
    // Direct fields from ModelSettings
    Integer maxTokens;
    Float temperature;
    Float topP;
    Duration timeout;
    Boolean parallelToolCalls;
    Integer seed;
    Float presencePenalty;
    Float frequencyPenalty;
    Map<String, Integer> logitBias;
    Reasoning reasoning;

    // ModelAttributes (nested)
    EncodingType encodingType;   // → ModelAttributes.encodingType
    Integer contextWindowSize;   // → ModelAttributes.contextWindowSize

    // SimpleOpenAIModelOptions
    ToolChoice toolChoice;       // → SimpleOpenAIModelOptions.toolChoice

    // Free-form passthrough
    Map<String, String> extraArgs;
}
```

`ModelTuning` provides a `merge(ModelTuning lhs, ModelTuning rhs)` method that
chains the existing `ModelSettings.merge()`, `ModelAttributes.merge()`, and
`SimpleOpenAIModelOptions.merge()` calls — **rhs wins if non-null**.

It also provides `toModelSettings()`, `toModelAttributes()`, and
`toModelOptions()` conversion methods that produce the framework types.

### 3.4 Mode defaults

- If a model defines **exactly one** mode, that mode is automatically the
  **default** — it is applied even when the model string does not specify a mode
  (e.g. `copilot/claude-sonnet-4.6` applies the sole mode's settings).
- If a model defines **multiple** modes, the user must specify one explicitly
  via `provider/model/mode`, or a `defaultMode` field on the model can designate
  the default.
- If a model defines **zero** modes, no mode-level override is applied — just the
  model-level (and provider-level) settings.

---

## 4. Resolution / precedence

Effective settings are built by **recursive bottom-up merge** through the
hierarchy, then the session/CLI override is applied on top:

```
provider-level defaults (ModelTuning)
   ⊕ model-level settings  (ModelTuning.merge: provider ⊕ model)
   ⊕ mode-level overrides  (ModelTuning.merge: above ⊕ mode)
   ⊕ session/CLI one-off overrides  (e.g. /set temperature 0.3)
```

Where `⊕` denotes `ModelTuning.merge(lhs, rhs)` — **rhs wins if non-null**.

- Computed independently for the `ModelSettings` chain, the nested
  `ModelAttributes` chain, **and** the separate `modelOptions`
  (`SimpleOpenAIModelOptions`) chain.
- `AgentFactory.createAgent` still applies its invariants **afterward** —
  `.withParallelToolCalls(false)` and `defaultModelSettings()` (context window
  `128_000`, `EncodingType.O200K_BASE`) — so the resolver output simply *feeds*
  it; the invariants remain the floor/ceiling.

### 4.1 Model string format

The model string is now `provider/model[/mode]`:

```
copilot/claude-sonnet-4.6           → provider=copilot, model=claude-sonnet-4.6, mode=(default or none)
copilot/claude-sonnet-4.6/coding    → provider=copilot, model=claude-sonnet-4.6, mode=coding
openrouter/anthropic/claude-3.5-sonnet  → provider=openrouter, model=anthropic/claude-3.5-sonnet, mode=(default or none)
openrouter/anthropic/claude-3.5-sonnet/planning → provider=openrouter, model=anthropic/claude-3.5-sonnet, mode=planning
```

Splitting logic: split on `/` with limit 3.
- `parts[0]` → provider
- `parts[1]` → model (may itself contain `/` if the provider uses slash-delimited model IDs — see below)
- `parts[2]` → mode (optional)

> **Model IDs with slashes**: some providers (OpenRouter) use model IDs like
> `anthropic/claude-3.5-sonnet`. To disambiguate, the split uses `limit=3`:
> `openrouter/anthropic/claude-3.5-sonnet/planning` → `["openrouter",
> "anthropic/claude-3.5-sonnet", "planning"]`. The model ID is everything between
> the first and last `/` (or between the first `/` and end if no mode is given).

### 4.2 Provider resolution

When the user runs `--model openrouter/anthropic/claude-3.5-sonnet` or
`/model openrouter/anthropic/claude-3.5-sonnet`:

1. Split on `/` with limit 3 → `provider = "openrouter"`,
   `modelName = "anthropic/claude-3.5-sonnet"`, `mode = null`.
2. Look up `provider` in the loaded `settings.yaml` (or built-in `copilot`).
3. If found and `type == openai` → build `SimpleOpenAI` with the config's
   `endpoint`, `apiKey`, `organizationId`, `projectId`, and `extraHeaders`.
4. If found and `type == azure` → build `SimpleOpenAIAzure` with the above
   **plus** `apiVersion`.
5. If `provider == "copilot"` → use the built-in `CopilotDirectProvider`
   (ignores `settings.yaml`).
6. If not found → **fall back to env-var behavior** (current code path) for
   backward compatibility, or error if no env vars are set.

> **Fallback design**: if `settings.yaml` is absent or has no entry for the
> requested provider, the factory falls back to the current env-var reads
> (`OPENAI_*`, `AZURE_*`, `COPILOT_PROXY_*`). This preserves backward
> compatibility — existing `.env` setups keep working without any config file.

### 4.3 Unknown model fallback

If `settings.yaml` has **no entry** for the requested model under the provider:

- **Warn** the user with a one-line message (e.g. `"No settings found for
  model 'claude-sonnet-4.6' under provider 'copilot'; using persona defaults."`)
- **Fall back to the persona's inline tuning** (the `tuning` section in the
  persona, or the legacy `modelSettings` / `modelOptions` fields for backward
  compatibility).
- If the persona also has no tuning, the framework defaults from
  `AgentFactory.defaultModelSettings()` apply.

---

## 5. Concrete config sketch (illustrative)

```yaml
# ~/.config/sai/settings.yaml — single file, hierarchical
# Secrets via ${ENV} interpolation; never commit keys.

providers:
  openai:
    type: openai
    endpoint: ${OPENAI_ENDPOINT:-https://api.openai.com/v1}
    apiKey:   ${OPENAI_API_KEY}
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
    apiKey:   ${OPENROUTER_API_KEY}
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

  opencode:
    type: openai
    endpoint: https://opencode.ai/api/v1
    apiKey:   ${OPENCODE_API_KEY}

  together:
    type: openai
    endpoint: https://api.together.xyz/v1
    apiKey:   ${TOGETHER_API_KEY}

  groq:
    type: openai
    endpoint: https://api.groq.com/openai/v1
    apiKey:   ${GROQ_API_KEY}

  azure:
    type: azure
    endpoint:   ${AZURE_ENDPOINT}
    apiKey:     ${AZURE_API_KEY}
    apiVersion: ${AZURE_API_VERSION:-2024-10-21}

  copilot-proxy:
    type: openai
    endpoint: https://models.github.ai/inference
    apiKey:   ${COPILOT_PROXY_TOKEN:-dummy}
    extraHeaders:
      Accept: "application/vnd.github+json"
      X-GitHub-Api-Version: "2022-11-28"

# copilot is NOT listed here — it is always available as a built-in provider.
```

### 5.1 Persona with `tuning` (reusing `ModelTuning`)

Personas now use the same `ModelTuning` class for their inline settings. The
legacy `modelSettings` / `modelOptions` fields are kept for backward
compatibility but `tuning` is preferred:

```yaml
# examples/personas/coder.yaml
agentId: coder
name: Coder
description: A coding-focused AI agent
model: copilot/claude-sonnet-4.6/coding
prompt: |
  You are an expert coder...
# New: unified tuning section (uses same ModelTuning class as settings.yaml)
tuning:
  temperature: 0.5
  toolChoice: AUTO
# Legacy (still works, but tuning takes precedence if both present):
# modelSettings:
#   temperature: 0.5
# modelOptions:
#   toolChoice: AUTO
```

### 5.2 `${ENV}` interpolation

`AgentConfigLoader.substituteEnvVars()` already uses
`org.apache.commons.text.StringSubstitutor` to replace `${VAR}` with
`System.getenv()` in persona files. The same mechanism applies to
`settings.yaml`:

- `${OPENAI_API_KEY}` → value of the env var (empty string if unset).
- `${OPENAI_ENDPOINT:-https://api.openai.com/v1}` → env var or default after
  `:-` (requires extending `StringSubstitutor` with a default-value resolver).
- Secrets **never** appear literally in config files; only `${ENV}` references.

### 5.3 `${VAR:-default}` implementation

Extend the existing `StringSubstitutor` usage with a custom
`StringSubstitutor` that parses the `:-` default-value syntax, matching
shell/bash convention:

```java
// Custom value resolver that handles ${VAR:-default}
private static String resolveWithDefaults(String content) {
    var substitutor = new StringSubstitutor(key -> {
        var colonDash = key.indexOf(":-");
        if (colonDash >= 0) {
            var varName = key.substring(0, colonDash);
            var defaultVal = key.substring(colonDash + 2);
            var val = System.getenv(varName);
            return val != null ? val : defaultVal;
        }
        var val = System.getenv(key);
        return val != null ? val : "";
    });
    return substitutor.replace(content);
}
```

This replaces the current `StringSubstitutor.replace(content, System.getenv())`
call in `AgentConfigLoader` and is reused for `settings.yaml`.

---

## 6. How it plugs into existing code (touch points, no code)

### 6.1 New `ModelTuning` class

A single `@Value @Builder @Jacksonized` class that carries all tuning fields
(temperature, topP, maxTokens, seed, penalties, reasoning, logitBias,
encodingType, contextWindowSize, toolChoice, extraArgs). Provides:

- `merge(ModelTuning lhs, ModelTuning rhs)` — chains `ModelSettings.merge()`,
  `ModelAttributes.merge()`, `SimpleOpenAIModelOptions.merge()`.
- `toModelSettings()` — converts to `ModelSettings`.
- `toModelAttributes()` — converts to `ModelAttributes`.
- `toModelOptions()` — converts to `SimpleOpenAIModelOptions`.

Used in: `settings.yaml` (provider/model/mode levels), persona files (`tuning`
section).

### 6.2 New `SettingsResolver`

Given `(provider, model, mode, settingsConfig, personaTuning, cliOverrides)`
returns effective `ModelSettings` + `SimpleOpenAIModelOptions` via the recursive
merge chain in §4.

```
effective = merge(
    provider.tuning,           // may be null
    model.tuning,              // may be null
    mode.tuning,               // may be null
    personaTuning,             // fallback if no settings.yaml entry
    cliOverrides               // session/CLI one-offs
)
```

If no `settings.yaml` entry exists for the model, `personaTuning` is used
directly (with a warning). If neither exists, framework defaults apply.

### 6.3 New `SettingsConfig` / `SettingsConfigLoader`

- `SettingsConfig` — root class for `settings.yaml` with a `providers` map.
- `ProviderEntry` — `type`, `endpoint`, `apiKey`, `apiVersion`,
  `organizationId`, `projectId`, `extraHeaders`, `tuning` (ModelTuning),
  `models` (Map<String, ModelEntry>).
- `ModelEntry` — `tuning` (ModelTuning), `defaultMode` (String, optional),
  `modes` (Map<String, ModeEntry>).
- `ModeEntry` — `tuning` (ModelTuning).
- `SettingsConfigLoader` — loads `settings.yaml` from `{configDir}/settings.yaml`
  with `${ENV}` / `${VAR:-default}` interpolation. Returns an empty config if the
  file does not exist (backward compatibility).

### 6.4 `ConfigurableProviderFactory` — config-driven providers

- **Load `settings.yaml`** at startup (from `{configDir}/settings.yaml`).
- Replace the hardcoded `switch` with a **lookup**:
  - If `provider == "copilot"` → use `CopilotDirectProvider` (built-in, as today).
  - If `provider` is in the loaded config → build `SimpleOpenAI` or
    `SimpleOpenAIAzure` from the config entry.
  - If `provider` is **not** in the config → fall back to env-var reads
    (current behavior for backward compatibility).
- The factory constructor changes from `(provider, mapper, okHttpClient)` to
  accept the loaded `SettingsConfig` (or a `ProviderEntry`). `SaiCommand` line
  241 and `AgentFactory.resolveProviderFactory()` line 194 are the two call
  sites.
- `extraHeaders` in config replaces the current `OPENAI_EXTRA_HEADERS` env-var
  parsing (comma-split `Key:Value` pairs). Config-driven `extraHeaders` is a
  proper `Map<String, String>` — cleaner than the current comma-delimited string.

### 6.5 `AgentFactory.createAgent`

Consume the resolved settings (from `SettingsResolver`) instead of only
`AgentConfig.getModelSettings()` / `getModelOptions()`. The `AgentConfig` may
carry a `tuning` field (preferred) or legacy `modelSettings` / `modelOptions`
(fallback).

### 6.6 `SlashCommandContext.rebuildAgent()` — the bug fix

On `/model`, **re-resolve** settings for the new model using
`SettingsResolver`. The current bug is that it reuses the old persona's
`modelSettings` / `modelOptions` — the fix is to look up the new model's
settings from `settings.yaml` and merge appropriately.

### 6.7 New `/mode` command

- `/mode` with no args → prints the current mode (or "(none)").
- `/mode <name>` → sets the active mode and rebuilds the agent. Does **not**
  change the model or provider — only the mode tier.
- Registered in `SlashRootCommand` alongside `/model`, `/persona`, `/skills`.

### 6.8 `Settings.java` / session

Persist the **active mode** alongside model + persona, restored on resume
(matching current model/persona persistence). The session extra data map gains a
`"mode"` key.

### 6.9 `AgentConfig` changes

- Add a `tuning` field of type `ModelTuning` (preferred).
- Keep `modelSettings` and `modelOptions` for backward compatibility.
- In `AgentFactory.createAgent`, if `tuning` is non-null, use it; otherwise fall
  back to `modelSettings` / `modelOptions`.
- The `model` field now accepts `provider/model[/mode]` format.

### 6.10 Doc/code drift to fold in

`AgentConfigLoader.resolvePersonaPath()` resolves simple names under
`{configDir}/persona/`, while `docs/reference/persona-format.md` says
`~/.config/sai/personas/`. Pick one and align both.

### 6.11 Documentation updates

`docs/reference/environment.md` currently documents all env vars
(`OPENAI_*`, `AZURE_*`, `COPILOT_*`). After this change:

- Env vars remain as a **fallback** but are no longer the primary configuration
  mechanism.
- Add a new `docs/reference/settings.md` documenting `settings.yaml` format,
  `type` field, `${ENV}` interpolation, hierarchical provider → model → mode
  structure, and the built-in `copilot` provider.
- Update `environment.md` to cross-reference `settings.yaml` and note that env
  vars are used when no config entry exists.

---

## 7. Backward compatibility & migration

### 7.1 No config file → env-var fallback

If `settings.yaml` is **absent**, the factory uses the current env-var reads.
Existing `.env` setups keep working without any config file. This is the
critical backward-compatibility guarantee.

### 7.2 Model tier fallback

If `settings.yaml` has **no entry** for the active model, **warn and fall back
to the persona's inline tuning** → today's behavior preserved; existing
personas keep working untouched.

### 7.3 Legacy persona fields

Personas with `modelSettings` / `modelOptions` (legacy) continue to work. The
new `tuning` field is preferred but both are supported. If both are present,
`tuning` takes precedence.

### 7.4 Built-in providers

`copilot` is always available as a built-in provider, regardless of
`settings.yaml`. The `copilot-proxy` provider can optionally be listed in
`settings.yaml` (as `type: openai` with the GitHub inference endpoint), but if
not listed, falls back to the current `COPILOT_PROXY_ENDPOINT` env var.

### 7.5 Migration path

1. **Phase 1** (this design): add `settings.yaml` support with env-var fallback.
   No existing setup breaks.
2. **Phase 2** (later): optional one-shot migration tool to extract inline
   persona settings into `settings.yaml` and generate a `settings.yaml` from
   current env vars.
3. **Phase 3** (later): deprecate direct env-var reads; require config file
   (with a clear error message pointing to the migration tool).

---

## 8. Trade-offs / risks

- **Single file can get large** — mitigated by the hierarchical structure which
  keeps related settings together (provider + its models + their modes in one
  block).
- **Repetition across providers** — if the same model is available on multiple
  providers, its settings must be repeated under each provider. This is
  intentional: the hierarchy is clean and provider-scoped. The `tuning` merge
  means provider-level defaults reduce repetition.
- **Same model id across providers** (e.g. `gpt-4o` on Azure vs OpenAI) ⇒ the
  model tier is naturally keyed by `provider/model` since models are nested
  inside providers.
- Secrets: keep API keys in **env only**; config references `${ENV}` (never
  commit keys).
- `merge()` is **shallow** for maps (`logitBias`, `extraHeaders`, `extraArgs`) —
  we must define map-merge semantics (replace vs deep-merge). *Default: replace
  (rhs map wins entirely if non-null).*
- **Provider name collisions**: if a user defines `openai` in `settings.yaml`
  **and** has `OPENAI_*` env vars set, the config entry wins (config > env).
  This is intentional — config is the new primary source.
- **`copilot` in config**: if a user mistakenly lists `copilot` in
  `settings.yaml`, it is **ignored** with a warning — the built-in
  `CopilotDirectProvider` always takes precedence.
- **Multiple OpenAI-compatible providers**: the env-var approach only supports
  one `OPENAI_*` set at a time. Config-driven providers solve this — you can
  have `openai/gpt-4o`, `openrouter/anthropic/claude-3.5`, and
  `groq/llama-3.1-70b` all configured simultaneously, each with its own
  `apiKey` and `endpoint`.
- **`extraHeaders` format change**: current `OPENAI_EXTRA_HEADERS` is a
  comma-delimited string (`Key:Value,Key2:Value2`). Config-driven
  `extraHeaders` is a `Map<String, String>`. The env-var fallback path must
  continue parsing the comma-delimited format for backward compatibility.
- **Model IDs with slashes**: OpenRouter uses `anthropic/claude-3.5-sonnet`.
  The `limit=3` split handles this, but users must be aware that
  `openrouter/anthropic/claude-3.5-sonnet` is provider=openrouter,
  model=anthropic/claude-3.5-sonnet (not three parts).

---

## 9. Answers to clarifying questions

| # | Question | Answer |
|---|----------|--------|
| 1 | Storage layout — three files or one `settings.yaml`? | **Single file** (`settings.yaml`) with `providers:` section. |
| 2 | Model keying / structure | **Hierarchical**: models nested inside provider, modes nested inside model. Provider-level defaults apply to all models; if model/mode provides settings, they recursively merge up. Repetition across providers is acceptable for clean structure. |
| 3 | Mode scope — global or model-scoped? | **Model-scoped** (nested inside model, not global). |
| 4 | Mode semantics — settings-only or also switch model? | **Settings-only** for v1. |
| 5 | Provider secrets — env-only? | **Yes, env-only** with `${ENV}` interpolation. |
| 6 | Model string format | **`provider/model[/mode]`** where mode is optional. If only one mode is defined for a model, it automatically becomes the default. |
| 7 | Runtime UX | **`/mode <name>`** is sufficient — only sets the mode, does not change model/provider. |
| 8 | Unknown model fallback | **Warn** and fall back to whatever is present in the persona. |
| 9 | Which knobs are overridable | **`modelSettings`, `modelOptions`, and `extraArgs`** — all overridable at every level. |
| 10 | Terminology | **"mode"** (not "profile") everywhere — config, CLI, docs. |
| 11 | Persona direction | **No deprecation**, but persona should use the same hierarchical model/mode structure (reusing the same `ModelTuning` classes) as `settings.yaml`. Update current personas to reflect this. |
| 12 | `${ENV:-default}` syntax | **Yes** — extend `StringSubstitutor` with `:-` default support, matching shell/bash convention. |

---

## 10. Next step

Review this updated design. Once approved, promote to a finalized design doc /
ADR — and only then, on explicit go-ahead, proceed to implementation with the
full Definition of Done from `AGENTS.md`: `mvn spotless:apply` +
`spotless:check`, compile, tests, and a SonarCloud Quality Gate = **OK** (no new
BLOCKER/CRITICAL).

---

## 11. Verification notes (checked against source)

| Claim | Evidence |
|-------|----------|
| Layered merge primitives exist with "rhs wins if non-null" | `ModelSettings.merge` (core `model/ModelSettings.java:103`), `ModelAttributes.merge` (`:48`), `SimpleOpenAIModelOptions.merge` (`:72`) |
| `ModelSettings` fields | `maxTokens, temperature, topP, timeout, parallelToolCalls, seed, presencePenalty, frequencyPenalty, logitBias, reasoning, modelAttributes` |
| `/model` reuses persona config, swaps only model string | `SlashCommandContext.rebuildAgent()` → `agentFactory.createAgent(modelName, currentAgentConfig.get())` |
| AgentFactory invariants applied after settings | `createAgent` forces `.withParallelToolCalls(false)`; `defaultModelSettings()` = ctx `128_000` + `EncodingType.O200K_BASE` |
| Provider creds come from env | `ConfigurableProviderFactory` reads `AZURE_*`, `OPENAI_*`, `COPILOT_PROXY_ENDPOINT` via `EnvLoader` |
| Per-model tuning duplicated in personas | `coder.yaml` temp 0.5; `kimi-coder.yaml` temp 0.6 / topP 0.95 / presencePenalty 0 / toolChoice AUTO |
| Persona path doc/code drift | `AgentConfigLoader` uses `{configDir}/persona/`; `docs/reference/persona-format.md` says `~/.config/sai/personas/` |
| `SimpleOpenAI` builder fields | `baseUrl, apiKey, organizationId, projectId, clientAdapter, retryConfig, objectMapper` — confirmed from `openAIModel()` usage in `ConfigurableProviderFactory` |
| `SimpleOpenAIAzure` builder adds `apiVersion` | `azureModel()` calls `.apiVersion(EnvLoader.readEnv("AZURE_API_VERSION", "2024-10-21"))` |
| `CopilotDirectProvider` cannot be config-driven | OAuth token exchange (`GET https://api.github.com/copilot_internal/v2/token`), URL-rewrite interceptor (strips `/v1`), header interceptor (injects Copilot headers + bearer token), scheduled refresh 60 s before expiry, 401/404 inline retry |
| `GithubCopilot` (copilot-proxy) can be config-driven | Extends `OpenAIProvider`; just custom base URL (`https://models.github.ai/inference`) + 3 static headers (`Accept`, `Content-Type`, `X-GitHub-Api-Version`) — maps to `type: openai` with `extraHeaders` |
| Factory is single-provider | `ConfigurableProviderFactory` holds one `provider` string; `SaiCommand:241` constructs it with `(provider, mapper, okHttpClient)` |
| `resolveProviderFactory` creates new factory for transforms | `AgentFactory:180-199` — only when `requestTransforms` present; else reuses shared factory |
| `${ENV}` interpolation already exists | `AgentConfigLoader.substituteEnvVars()` uses `StringSubstitutor.replace(content, System.getenv())` |
| `extraHeaders` current format | `OPENAI_EXTRA_HEADERS` env var, comma-delimited `Key:Value,Key2:Value2`, parsed in `openAIModel()` at lines 120-137 |
| `ModelAttributes.merge` semantics | Uses `DEFAULT_WINDOW_SIZE` / `DEFAULT_ENCODING_TYPE` as sentinels — lhs wins if non-default, else rhs. This is subtly different from "rhs wins if non-null" and must be accounted for in `ModelTuning.merge()`. |
| `SimpleOpenAIModelOptions.merge` | Instance method: `this.merge(other)` — `Objects.requireNonNullElse(other.toolChoice, this.toolChoice)`. |
| `SimpleOpenAIModelOptions` fields | `toolChoice` (ToolChoice enum: REQUIRED, AUTO), `tokenCountingConfig` (TokenCountingConfig). `DEFAULT` = AUTO + DEFAULT token counting. |
