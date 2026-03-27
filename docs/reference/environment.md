# Environment Variables

SAI supports configuration through environment variables for LLM provider authentication and settings. Variables can be set in your shell environment or in a `.env` file in the config directory (`~/.config/sai/.env`).

## Overview

SAI reads environment variables in the following order (later sources override earlier ones):

1. System environment variables
2. `.env` file in `~/.config/sai/`
3. `.env` file in `--config-dir` (if specified)

!!! tip "Configuration Priority"
    Command-line flags (like `--model`) take precedence over environment variables, which take precedence over default values.

---

## OpenAI Provider

Configuration for OpenAI API (including OpenAI-compatible providers like OpenRouter, Groq, etc.).

### OPENAI_API_KEY

**Description**: Authentication key for OpenAI API  
**Required**: Yes (when using OpenAI provider)  
**Default**: None  
**Format**: `sk-...` (OpenAI API key format)

```bash
export OPENAI_API_KEY=sk-proj-abc123xyz...
```

**How to obtain**:

1. Sign up at [https://platform.openai.com](https://platform.openai.com)
2. Navigate to API Keys section
3. Create a new secret key
4. Copy and store securely (shown only once)

---

### OPENAI_ENDPOINT

**Description**: Base URL for OpenAI API endpoint  
**Required**: No  
**Default**: `https://api.openai.com/v1`  

```bash
export OPENAI_ENDPOINT=https://api.openai.com/v1
```

**Use cases**:

- **OpenAI-compatible providers**: Point to alternative endpoints (OpenRouter, Together AI, etc.)
- **Proxies**: Route through Helicone, LangSmith, or custom proxy
- **Self-hosted**: Use local or on-premises OpenAI-compatible servers

**Examples**:

```bash
# OpenRouter
export OPENAI_ENDPOINT=https://openrouter.ai/api/v1

# Together AI
export OPENAI_ENDPOINT=https://api.together.xyz/v1

# Local vLLM server
export OPENAI_ENDPOINT=http://localhost:8000/v1

# Helicone proxy
export OPENAI_ENDPOINT=https://oai.helicone.ai/v1
```

---

### OPENAI_ORGANIZATION

**Description**: OpenAI organization ID for billing and access control  
**Required**: No  
**Default**: None  
**Format**: `org-...`

```bash
export OPENAI_ORGANIZATION=org-abc123xyz...
```

**When to use**:

- You belong to multiple OpenAI organizations
- You need to bill usage to a specific organization
- Required by your organization's OpenAI setup

---

### OPENAI_PROJECT_ID

**Description**: OpenAI project ID for usage tracking and organization  
**Required**: No  
**Default**: None  
**Format**: `proj-...`

```bash
export OPENAI_PROJECT_ID=proj-abc123xyz...
```

**When to use**:

- You have multiple projects within an organization
- You need project-level usage tracking
- Required by your organization's OpenAI setup

---

### OPENAI_EXTRA_HEADERS

**Description**: Additional HTTP headers to include in OpenAI API requests  
**Required**: No  
**Default**: None  
**Format**: Comma-separated `Key:Value` pairs

```bash
export OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer xyz,Custom-Header:value
```

**Use cases**:

- **Helicone integration**: Add authentication and metadata headers
- **LangSmith tracing**: Include tracing headers
- **Custom proxies**: Pass authentication or routing information
- **Rate limiting**: Add custom rate limit identifiers

**Examples**:

```bash
# Helicone observability
export OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer sk-helicone-abc123,Helicone-Cache-Enabled:true

# LangSmith tracing
export OPENAI_EXTRA_HEADERS=X-LangSmith-Trace:true,X-LangSmith-Project:my-project

# Custom authentication
export OPENAI_EXTRA_HEADERS=X-API-Version:v2,X-Client-ID:sai-cli
```

---

## Azure OpenAI Provider

Configuration for Azure OpenAI Service.

### AZURE_ENDPOINT

**Description**: Base URL for Azure OpenAI resource  
**Required**: Yes (when using Azure provider)  
**Default**: None  
**Format**: `https://<resource-name>.openai.azure.com`

```bash
export AZURE_ENDPOINT=https://my-resource.openai.azure.com
```

**How to obtain**:

1. Create an Azure OpenAI resource in Azure Portal
2. Navigate to "Keys and Endpoint" section
3. Copy the "Endpoint" value

!!! warning "Endpoint Format"
    The endpoint should be the base URL **without** `/openai/deployments/` or other path components. SAI will construct the full URL automatically.

---

### AZURE_API_KEY

**Description**: Authentication key for Azure OpenAI Service  
**Required**: Yes (when using Azure provider)  
**Default**: None  

```bash
export AZURE_API_KEY=abc123xyz456...
```

**How to obtain**:

1. Navigate to your Azure OpenAI resource in Azure Portal
2. Go to "Keys and Endpoint" section
3. Copy either "Key 1" or "Key 2"

**Security notes**:

- Rotate keys periodically for security
- Use Azure Key Vault for production deployments
- Never commit keys to version control

---

### AZURE_API_VERSION

**Description**: Azure OpenAI API version to use  
**Required**: No  
**Default**: `2024-10-21`  

```bash
export AZURE_API_VERSION=2024-10-21
```

**Available versions**:

- `2024-10-21` (recommended, latest stable)
- `2024-08-01-preview`
- `2024-06-01`
- `2024-02-01`

**When to override**:

- Using preview features only available in newer API versions
- Maintaining compatibility with older deployments
- Testing new API features

!!! info "Version Selection"
    Use the default unless you have a specific reason to change it. Newer versions may introduce breaking changes or require updated model deployments.

---

## Copilot Proxy Provider

Configuration for GitHub Copilot via [copilot-proxy](https://github.com/your-org/copilot-proxy).

### COPILOT_PROXY_ENDPOINT

**Description**: URL for copilot-proxy server  
**Required**: No  
**Default**: `http://localhost:4141`  

```bash
export COPILOT_PROXY_ENDPOINT=http://localhost:4141
```

**Setup**:

1. Install and run [copilot-proxy](https://github.com/your-org/copilot-proxy)
2. Authenticate with GitHub Copilot
3. Start the proxy server (defaults to port 4141)
4. SAI will automatically connect to the proxy

**Custom configurations**:

```bash
# Custom port
export COPILOT_PROXY_ENDPOINT=http://localhost:8080

# Remote proxy server
export COPILOT_PROXY_ENDPOINT=https://copilot-proxy.internal.company.com

# Docker container on custom network
export COPILOT_PROXY_ENDPOINT=http://copilot-proxy:4141
```

**Models available via Copilot Proxy**:

- `copilot-proxy/claude-sonnet-4.6` (Anthropic Claude Sonnet)
- `copilot-proxy/claude-haiku-4.5` (Anthropic Claude Haiku, default)
- `copilot-proxy/gpt-4o` (OpenAI GPT-4o)
- `copilot-proxy/gpt-4o-mini` (OpenAI GPT-4o Mini)
- `copilot-proxy/gemini-3.1-pro-preview` (Google Gemini Pro)

!!! tip "GitHub Copilot Subscription"
    Using copilot-proxy requires an active GitHub Copilot subscription. This provides cost-effective access to multiple frontier models.

---

## General Variables

### MODEL

**Description**: Default LLM model to use  
**Required**: No  
**Default**: `copilot-proxy/claude-haiku-4.5`  
**Format**: `<provider>/<model-name>`

```bash
export MODEL=copilot-proxy/claude-sonnet-4.6
```

**Provider prefixes**:

- `openai/` - OpenAI models (requires OPENAI_API_KEY)
- `azure/` - Azure OpenAI models (requires AZURE_ENDPOINT and AZURE_API_KEY)
- `copilot-proxy/` - GitHub Copilot via proxy (requires copilot-proxy running)

**Examples**:

```bash
# OpenAI
export MODEL=openai/gpt-4o
export MODEL=openai/gpt-4o-mini
export MODEL=openai/o1-preview

# Azure (use deployment name, not model name)
export MODEL=azure/gpt-4o-deployment
export MODEL=azure/my-claude-deployment

# Copilot Proxy
export MODEL=copilot-proxy/claude-sonnet-4.6
export MODEL=copilot-proxy/gpt-4o
export MODEL=copilot-proxy/gemini-3.1-pro-preview
```

!!! note "Override Priority"
    The `--model` / `-m` CLI flag overrides the MODEL environment variable, which overrides the default value.

---

## Configuration File Templates

### .env Template for OpenAI

Create `~/.config/sai/.env`:

```bash
# OpenAI Configuration
OPENAI_API_KEY=sk-proj-your_key_here

# Optional: Custom endpoint (for proxies, OpenRouter, etc.)
# OPENAI_ENDPOINT=https://api.openai.com/v1

# Optional: Organization settings
# OPENAI_ORGANIZATION=org-abc123
# OPENAI_PROJECT_ID=proj-xyz789

# Optional: Additional headers (Helicone, LangSmith, etc.)
# OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer sk-helicone-xyz,Helicone-Cache-Enabled:true

# Default model
MODEL=openai/gpt-4o
```

### .env Template for Azure OpenAI

Create `~/.config/sai/.env`:

```bash
# Azure OpenAI Configuration
AZURE_ENDPOINT=https://your-resource-name.openai.azure.com
AZURE_API_KEY=your_azure_api_key_here

# Optional: API version (defaults to 2024-10-21)
# AZURE_API_VERSION=2024-10-21

# Default model (use your deployment name)
MODEL=azure/gpt-4o-deployment
```

### .env Template for GitHub Copilot Proxy

Create `~/.config/sai/.env`:

```bash
# Copilot Proxy Configuration (requires copilot-proxy running)
# Default endpoint is http://localhost:4141

# Optional: Custom proxy endpoint
# COPILOT_PROXY_ENDPOINT=http://localhost:4141

# Default model
MODEL=copilot-proxy/claude-sonnet-4.6
```

### .env Template for Multi-Provider Setup

Create `~/.config/sai/.env`:

```bash
# Multi-Provider Configuration
# Configure all providers you want to use

# OpenAI
OPENAI_API_KEY=sk-proj-your_openai_key

# Azure OpenAI
AZURE_ENDPOINT=https://your-resource.openai.azure.com
AZURE_API_KEY=your_azure_key

# Copilot Proxy (requires copilot-proxy running)
COPILOT_PROXY_ENDPOINT=http://localhost:4141

# Default model (can be switched with --model flag)
MODEL=copilot-proxy/claude-haiku-4.5

# Optional: Helicone observability for OpenAI
# OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer sk-helicone-xyz
```

---

## Environment Variable Loading

SAI loads environment variables in this order:

1. **System environment**: Variables set in your shell (`.bashrc`, `.zshrc`, etc.)
2. **Default config directory**: `~/.config/sai/.env`
3. **Custom config directory**: `--config-dir=<path>/.env` (if specified)

Later sources override earlier ones, and command-line flags override all environment variables.

**Example priority:**

```bash
# System environment
export MODEL=openai/gpt-4

# ~/.config/sai/.env
MODEL=copilot-proxy/claude-haiku-4.5

# Command line
sai --model=copilot-proxy/claude-sonnet-4.6 "Hello"

# Result: Uses copilot-proxy/claude-sonnet-4.6 (CLI flag wins)
```

---

## Security Best Practices

### Protect API Keys

!!! danger "Never Commit Secrets"
    Never commit `.env` files or API keys to version control. Add `.env` to `.gitignore`.

```bash
# .gitignore
.env
*.env
**/.env
```

### File Permissions

Restrict access to `.env` files:

```bash
chmod 600 ~/.config/sai/.env
```

### Use Secret Management

For production or shared environments:

- **Azure**: Use Azure Key Vault with managed identities
- **AWS**: Use AWS Secrets Manager or Parameter Store
- **Docker**: Use Docker secrets or environment injection
- **Kubernetes**: Use Kubernetes secrets and ConfigMaps

### Rotate Keys Regularly

- Set calendar reminders to rotate API keys quarterly
- Use different keys for development and production
- Revoke old keys after rotation

### Environment-Specific Configurations

Use different `.env` files for different environments:

```bash
# Development
cp ~/.config/sai/.env.dev ~/.config/sai/.env

# Production
cp ~/.config/sai/.env.prod ~/.config/sai/.env

# Or use custom config directories
sai --config-dir=~/.config/sai-prod ...
```

---

## Troubleshooting

### Missing API Key

**Error**: `OPENAI_API_KEY not found`

**Solution**:

```bash
# Check if variable is set
echo $OPENAI_API_KEY

# Set in current shell
export OPENAI_API_KEY=sk-proj-...

# Or add to .env file
echo "OPENAI_API_KEY=sk-proj-..." >> ~/.config/sai/.env
```

### Wrong Endpoint Format

**Error**: `Connection refused` or `404 Not Found`

**Solution**: Verify endpoint format:

```bash
# Azure - should NOT include /openai/deployments/
AZURE_ENDPOINT=https://my-resource.openai.azure.com  # ✓ Correct
AZURE_ENDPOINT=https://my-resource.openai.azure.com/openai/deployments/  # ✗ Wrong

# OpenAI - should include /v1
OPENAI_ENDPOINT=https://api.openai.com/v1  # ✓ Correct
OPENAI_ENDPOINT=https://api.openai.com  # ✗ Wrong
```

### Environment Variables Not Loading

**Check loading order**:

```bash
# 1. Verify .env file exists
ls -la ~/.config/sai/.env

# 2. Check file permissions (should be readable)
ls -l ~/.config/sai/.env

# 3. Verify file contents
cat ~/.config/sai/.env

# 4. Test with debug mode
sai --debug "Test message"
```

### Copilot Proxy Connection Failed

**Error**: `Connection refused to http://localhost:4141`

**Solution**:

1. Verify copilot-proxy is running:

```bash
curl http://localhost:4141/health
```

2. Check proxy logs for errors
3. Restart copilot-proxy if needed
4. Verify COPILOT_PROXY_ENDPOINT matches actual port

---

## See Also

- [CLI Options](cli-options.md) - Command-line flags and options
- [Subcommands](subcommands.md) - Session management commands
- [Configuration Guide](../getting-started/configuration.md) - Detailed provider setup
- [Persona Format](persona-format.md) - Custom persona configuration
