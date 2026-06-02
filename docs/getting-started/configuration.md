# Configuration

SAI requires configuration to connect to your AI model provider. This guide covers all configuration options.

## Model Providers

SAI supports three types of model providers:

| Provider | Description | Use Case |
|----------|-------------|----------|
| **openai** | OpenAI API and compatible endpoints | OpenAI, Cerebras, OpenRouter, etc. |
| **azure** | Azure OpenAI Service | Enterprise Azure deployments |
| **copilot** | GitHub Copilot direct integration | Using GitHub Copilot with your subscription |

## Model Selection

Models are specified in the format: `<provider>/<model-name>`

### Command Line

```bash
# Override with --model flag
java -jar target/sai-1.0-SNAPSHOT.jar --model openai/gpt-4o

# Short form
java -jar target/sai-1.0-SNAPSHOT.jar -m copilot/claude-sonnet-4.6
```

### Persona File

```yaml
# In your persona YAML
agentId: my-agent
name: My Agent
model: azure/gpt-4.1
```

### Default

If no model is specified, SAI defaults to: **`copilot/claude-haiku-4.5`**

## OpenAI Configuration

For OpenAI and compatible endpoints.

### Required Environment Variables

```bash
export OPENAI_API_KEY=your_api_key_here
```

### Optional Environment Variables

```bash
# Custom endpoint (default: https://api.openai.com/v1)
export OPENAI_ENDPOINT=https://api.openai.com/v1

# Organization ID
export OPENAI_ORGANIZATION=org_...

# Project ID
export OPENAI_PROJECT_ID=proj_...

# Extra headers (comma-separated Key:Value pairs)
export OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer xyz,Another-Header:abc
```

### Example: Using OpenRouter

```bash
export OPENAI_API_KEY=sk-or-v1-...
export OPENAI_ENDPOINT=https://openrouter.ai/api/v1
```

### Example: Using Cerebras

```bash
export OPENAI_API_KEY=csk-...
export OPENAI_ENDPOINT=https://api.cerebras.ai/v1
```

### Example .env File

```env
OPENAI_API_KEY=sk-proj-...
OPENAI_ORGANIZATION=org_...
OPENAI_PROJECT_ID=proj_...
# OPENAI_ENDPOINT=https://api.openai.com/v1
```

## Azure OpenAI Configuration

For Azure-hosted OpenAI models.

### Required Environment Variables

```bash
export AZURE_ENDPOINT=https://your-resource.openai.azure.com
export AZURE_API_KEY=your_azure_api_key
```

### Optional Environment Variables

```bash
# API version (default: 2024-10-21)
export AZURE_API_VERSION=2024-10-21
```

### Example .env File

```env
AZURE_ENDPOINT=https://my-openai-resource.openai.azure.com
AZURE_API_KEY=abc123...
AZURE_API_VERSION=2024-10-21
```

### Usage

```bash
# Use Azure model
java -jar target/sai-1.0-SNAPSHOT.jar --model azure/gpt-4o
```

## Copilot Configuration

For direct GitHub Copilot integration (no proxy server required).

### Prerequisites

1. Authenticate with GitHub Copilot:
   ```bash
   npx copilot-api auth
   ```

2. This stores your GitHub OAuth token at:
   ```
   ~/.local/share/copilot-api/github_token
   ```

3. **No server needed** - SAI connects directly to GitHub Copilot API

### Environment Variables

No environment variables required! Authentication token is read automatically from:
```
~/.local/share/copilot-api/github_token
```

### Usage

```bash
# Default (uses copilot/claude-haiku-4.5)
java -jar target/sai-1.0-SNAPSHOT.jar

# Or specify model explicitly
java -jar target/sai-1.0-SNAPSHOT.jar --model copilot/gpt-4o
java -jar target/sai-1.0-SNAPSHOT.jar --model copilot/claude-sonnet-4.6
```

### Available Models

- `copilot/claude-sonnet-4.6` - Anthropic Claude Sonnet (most capable)
- `copilot/claude-haiku-4.5` - Anthropic Claude Haiku (fast, default)
- `copilot/gpt-4o` - OpenAI GPT-4o
- `copilot/gpt-4o-mini` - OpenAI GPT-4o Mini
- `copilot/o1-mini` - OpenAI o1-mini
- `copilot/o1-preview` - OpenAI o1-preview
- `copilot/gemini-2.0-flash-exp` - Google Gemini 2.0 Flash

### Enterprise/Business Setup

For GitHub Copilot Business or Enterprise:

1. Set custom endpoint (if required):
   ```bash
   export COPILOT_ENDPOINT=https://your-enterprise-endpoint
   ```

2. Token location remains the same:
   ```
   ~/.local/share/copilot-api/github_token
   ```

### Troubleshooting

**Error: Could not find GitHub token**

Run authentication:
```bash
npx copilot-api auth
```

**Error: 401 Unauthorized**

Token may have expired. Re-authenticate:
```bash
rm ~/.local/share/copilot-api/github_token
npx copilot-api auth
```

## Directory Structure

SAI uses two main directories for configuration and data:

### Config Directory

**Default:** `~/.config/sai/`

```
~/.config/sai/
├── persona/           # Persona files (YAML/JSON)
│   ├── reviewer.yaml
│   └── coder.yaml
└── skills/            # Agent skills
    ├── my-skill/
    │   └── SKILL.md
    └── another-skill/
        └── SKILL.md
```

**Override:**
```bash
java -jar target/sai-1.0-SNAPSHOT.jar --config-dir /path/to/config
```

### Data Directory

**Default:** `~/.local/state/sai/`

```
~/.local/state/sai/
└── sessions/
    ├── abc123-.../
    │   ├── session.json
    │   └── messages/
    └── def456-.../
        ├── session.json
        └── messages/
```

**Override:**
```bash
java -jar target/sai-1.0-SNAPSHOT.jar --data-dir /path/to/data
```

## Environment File

Create a `.env` file in your project root or home directory:

```env
# OpenAI
OPENAI_API_KEY=sk-proj-...

# Or Azure
# AZURE_ENDPOINT=https://your-resource.openai.azure.com
# AZURE_API_KEY=your_key

# Or Copilot Proxy (optional)
# COPILOT_PROXY_ENDPOINT=http://localhost:4141
```

SAI will automatically load `.env` files from:

1. Current working directory
2. Home directory (`~/.env`)

## Configuration Priority

When the same setting is configured in multiple places, SAI uses this priority (highest to lowest):

1. **Command-line flags** (--model, --config-dir, etc.)
2. **Environment variables** (OPENAI_API_KEY, etc.)
3. **Persona file** (model, skills, etc.)
4. **Defaults** (copilot/claude-haiku-4.5)

## Verify Configuration

Test your configuration:

```bash
# Quick test
echo "Hello, can you confirm you're working?" | java -jar target/sai-1.0-SNAPSHOT.jar
```

If configured correctly, you should get a response from the AI model.

## Next Steps

- [Quick Start](quickstart.md) - Run your first session
- [Running SAI](../guides/running.md) - Learn about different modes
- [Personas](../guides/personas.md) - Customize agent behavior
- [Environment Variables Reference](../reference/environment.md) - Complete variable list

## Troubleshooting

### Authentication Errors

**Problem:** `401 Unauthorized` or `Invalid API key`

!!! tip "Solution"
    - Verify your API key is correct and active
    - Check for leading/trailing spaces in environment variables
    - Ensure you're using the right provider (openai vs azure vs copilot)

### Connection Errors

**Problem:** `Connection refused` or `Timeout`

!!! tip "Solution"
    - For copilot: Ensure the proxy is running (`copilot-api`)
    - For custom endpoints: Verify the OPENAI_ENDPOINT URL is correct
    - Check your firewall/network settings

### Model Not Found

**Problem:** `Model not found` or `Invalid model`

!!! tip "Solution"
    - Verify the model name is correct for your provider
    - Check your subscription includes access to that model
    - For Azure: Ensure the deployment name matches

### Environment Variables Not Loaded

**Problem:** Configuration not picked up

!!! tip "Solution"
    - Export variables in current shell: `export OPENAI_API_KEY=...`
    - Or use `.env` file in current directory
    - Or pass values directly: `OPENAI_API_KEY=... java -jar ...`
