# Persona Format

Personas in SAI define agent behaviors, capabilities, and configurations. They are defined using YAML or JSON files and can be loaded with the `--persona` / `-p` flag.

## Overview

A persona is a configuration file that specifies:

- **Identity**: Agent name and description
- **Model**: Which LLM to use and its settings
- **Behavior**: Custom system prompt defining the agent's role
- **Tools**: MCP servers providing additional capabilities
- **Skills**: Agent Skills directories for specialized workflows

Personas allow you to create specialized agents for different tasks (coding, planning, reviewing, etc.) without modifying SAI itself.

---

## File Locations

SAI searches for persona files in the following locations (in order):

1. **Exact path**: If `-p` specifies a file path (`.yaml`, `.yml`, or `.json`)
2. **Config directory**: `~/.config/sai/personas/<name>.yaml`
3. **Config directory** (JSON): `~/.config/sai/personas/<name>.json`
4. **Built-in examples**: `examples/personas/<name>.yaml` (in SAI repository)

**Examples**:

```bash
# Load from exact path
sai -p /path/to/my-persona.yaml

# Load from config directory by name
sai -p coder  # Looks for ~/.config/sai/personas/coder.yaml

# Load from built-in examples
sai -p reviewer  # Looks for examples/personas/reviewer.yaml
```

---

## Schema

### YAML Format

```yaml
# Required Fields
agentId: unique-agent-id
name: Human-Readable Agent Name
description: Brief description of the agent's purpose and capabilities.

# Optional Fields
model: provider/model-name
modelSettings:
  temperature: 0.7
  maxTokens: 4096
  topP: 1.0

prompt: |
  Multi-line system prompt defining the agent's role,
  behavior, and instructions.

mcp:
  mcpServers:
    server-name:
      type: stdio
      command: command-name
      args: []
      env:
        KEY: value
      exposedTools:
        - tool1
        - tool2

skillDirectories:
  - /path/to/skills/directory

skillNames:
  - skill-name-1
  - skill-name-2
```

### JSON Format

```json
{
  "agentId": "unique-agent-id",
  "name": "Human-Readable Agent Name",
  "description": "Brief description of the agent's purpose and capabilities.",
  "model": "provider/model-name",
  "modelSettings": {
    "temperature": 0.7,
    "maxTokens": 4096,
    "topP": 1.0
  },
  "prompt": "Multi-line system prompt defining the agent's role,\nbehavior, and instructions.",
  "mcp": {
    "mcpServers": {
      "server-name": {
        "type": "stdio",
        "command": "command-name",
        "args": [],
        "env": {
          "KEY": "value"
        },
        "exposedTools": ["tool1", "tool2"]
      }
    }
  },
  "skillDirectories": ["/path/to/skills/directory"],
  "skillNames": ["skill-name-1", "skill-name-2"]
}
```

---

## Field Reference

### agentId (required)

**Type**: String  
**Description**: Unique identifier for the agent  
**Format**: Lowercase alphanumeric with hyphens (kebab-case)  
**Examples**: `sai-agent`, `sai-coder`, `sai-planner`, `code-reviewer`

```yaml
agentId: sai-coder
```

**Validation rules**:

- Must be unique across all personas
- Use descriptive names that indicate the agent's purpose
- Recommended format: `sai-<purpose>` or `<organization>-<purpose>`

---

### name (required)

**Type**: String  
**Description**: Human-readable display name for the agent  
**Examples**: `Sai Agent`, `Sai Coder`, `Code Reviewer`

```yaml
name: Sai Coder
```

**Best practices**:

- Use title case
- Keep it concise (2-4 words)
- Make it descriptive of the agent's role

---

### description (required)

**Type**: String  
**Description**: Brief summary of the agent's purpose, capabilities, and use cases  
**Length**: 1-3 sentences recommended

```yaml
description: Expert coding agent with codebase knowledge graph capabilities for intelligent code exploration and development.
```

**Best practices**:

- Explain what the agent does and when to use it
- Mention key capabilities or tools
- Keep it clear and actionable

---

### model (optional)

**Type**: String  
**Description**: LLM model to use for this persona  
**Format**: `<provider>/<model-name>`  
**Default**: Falls back to `MODEL` environment variable or `copilot-proxy/claude-haiku-4.5`

```yaml
model: copilot-proxy/claude-sonnet-4.6
```

**Provider prefixes**:

| Provider | Prefix | Example |
|----------|--------|---------|
| OpenAI | `openai/` | `openai/gpt-4o` |
| Azure OpenAI | `azure/` | `azure/gpt-4o-deployment` |
| Copilot Proxy | `copilot-proxy/` | `copilot-proxy/claude-sonnet-4.6` |

**Common models**:

```yaml
# OpenAI
model: openai/gpt-4o
model: openai/gpt-4o-mini
model: openai/o1-preview

# Azure (use deployment name)
model: azure/my-gpt4-deployment

# Copilot Proxy
model: copilot-proxy/claude-sonnet-4.6
model: copilot-proxy/claude-haiku-4.5
model: copilot-proxy/gpt-4o
model: copilot-proxy/gemini-3.1-pro-preview
```

**Override priority**:

1. CLI flag: `--model` / `-m`
2. Persona file: `model` field
3. Environment: `MODEL` variable
4. Default: `copilot-proxy/claude-haiku-4.5`

---

### modelSettings (optional)

**Type**: Object  
**Description**: LLM sampling parameters and generation settings  
**Default**: Provider-specific defaults

```yaml
modelSettings:
  temperature: 0.7
  maxTokens: 4096
  topP: 1.0
```

#### temperature

**Type**: Number (0.0 - 2.0)  
**Description**: Controls randomness in generation  
**Default**: Provider-specific (typically 1.0)

- `0.0` - Deterministic, focused (good for code generation, factual tasks)
- `0.5` - Balanced (good for general conversation, planning)
- `1.0` - Default randomness (creative, varied responses)
- `1.5+` - Highly creative (experimental, may be inconsistent)

```yaml
modelSettings:
  temperature: 0.3  # Low temperature for coding tasks
```

#### maxTokens

**Type**: Integer  
**Description**: Maximum tokens to generate in response  
**Default**: Model-specific maximum

```yaml
modelSettings:
  maxTokens: 8192  # Allow longer responses
```

**Common values**:

- `1024` - Short responses
- `4096` - Standard responses (default for many models)
- `8192` - Long-form content
- `16384+` - Very long content (if model supports)

#### topP

**Type**: Number (0.0 - 1.0)  
**Description**: Nucleus sampling threshold  
**Default**: 1.0

```yaml
modelSettings:
  topP: 0.9  # Slightly more focused sampling
```

**Usage**:

- `1.0` - Consider all tokens (default)
- `0.9` - Consider top 90% probability mass
- Lower values make output more focused and deterministic

---

### prompt (optional)

**Type**: String (multi-line)  
**Description**: System prompt defining the agent's role, behavior, and capabilities  
**Format**: Markdown-formatted text with clear sections

```yaml
prompt: |
  # Role
  You are an Expert Software Engineer.

  # Responsibilities
  - Write clean, maintainable code
  - Follow best practices and design patterns
  - Provide clear explanations

  # Guidelines
  - Be concise and actionable
  - Use code blocks with syntax highlighting
  - Test your changes before committing
```

**Best practices**:

- **Structure**: Use clear headings and sections
- **Clarity**: Be explicit about what the agent should and shouldn't do
- **Examples**: Include example workflows when helpful
- **Tools**: Document available tools and when to use them
- **Output**: Specify expected output format and style

**Common sections**:

- Role: Define who the agent is
- Context & Setup: Initial orientation steps
- Tools: Available capabilities and how to use them
- Workflow: Step-by-step processes
- Best Practices: Guidelines and conventions
- Output Guidelines: Format and style expectations

---

### mcp (optional)

**Type**: Object  
**Description**: MCP (Model Context Protocol) server configuration for tool access  
**Default**: No MCP servers

```yaml
mcp:
  mcpServers:
    server-name:
      type: stdio
      command: command-name
      args: []
      env:
        KEY: value
      exposedTools:
        - tool1
        - tool2
```

#### MCP Server Types

**stdio** (Standard I/O):

- Launches a local process
- Communicates via stdin/stdout
- Best for local tools and CLI utilities

```yaml
mcp:
  mcpServers:
    codebase-memory-mcp:
      type: stdio
      command: codebase-memory-mcp
      args: []
```

**http** (HTTP/Remote):

- Connects to remote MCP server via HTTP
- Best for cloud services and APIs
- Supports authentication headers

```yaml
mcp:
  mcpServers:
    exa-search:
      type: http
      url: https://mcp.exa.ai/mcp?exaApiKey=${EXA_API_KEY}
      timeout: 30000
      headers:
        Authorization: "Bearer ${API_TOKEN}"
```

#### MCP Server Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | Yes | Server type: `stdio` or `http` |
| `command` | String | Yes (stdio) | Command to execute for stdio servers |
| `args` | Array | No | Command-line arguments |
| `env` | Object | No | Environment variables for the process |
| `url` | String | Yes (http) | URL for HTTP servers |
| `timeout` | Integer | No | Timeout in milliseconds (HTTP only) |
| `headers` | Object | No | HTTP headers (HTTP only) |
| `exposedTools` | Array | No | Whitelist of tools to expose (empty = all) |

---

### skillDirectories (optional)

**Type**: Array of Strings  
**Description**: Directories containing Agent Skills definitions  
**Default**: Empty (no additional skill directories)

```yaml
skillDirectories:
  - /home/user/.config/sai/skills
  - /opt/company/shared-skills
```

**About Agent Skills**:

Agent Skills are a specification for defining reusable agent capabilities through YAML files. Each skill contains:

- Instructions for the agent
- Reference documentation
- Custom tools or workflows

**Use cases**:

- Organization-specific workflows
- Domain-specific knowledge and processes
- Reusable automation patterns
- Team-shared agent capabilities

---

### skillNames (optional)

**Type**: Array of Strings  
**Description**: Specific Agent Skills to activate by name  
**Default**: Empty (no skills activated)

```yaml
skillNames:
  - git-workflow
  - code-review-checklist
  - deployment-automation
```

**How it works**:

1. Skills are searched in `skillDirectories` (and default locations)
2. Each skill in `skillNames` is loaded and activated
3. The skill's instructions are injected into the agent's context
4. The agent has access to the skill's tools and reference docs

---

## Complete Examples

### Example 1: Basic Agent

Minimal persona with just required fields.

```yaml
agentId: sai-agent
name: Sai Agent
description: Default helpful CLI assistant persona for SAI.
model: copilot-proxy/claude-haiku-4.5
modelSettings:
  temperature: 1
prompt: |
  You are a helpful assistant for the command line. Be concise and actionable.
  Prefer bullet points for multi-step answers. Confirm destructive actions.
```

**Use case**: General-purpose CLI assistant for quick questions and tasks.

---

### Example 2: Coding Agent with MCP Tools

Advanced persona with codebase analysis capabilities.

```yaml
agentId: sai-coder
name: Sai Coder
description: Expert coding agent with codebase knowledge graph capabilities for intelligent code exploration and development.
model: copilot-proxy/claude-sonnet-4.6

modelSettings:
  temperature: 0.5

mcp:
  mcpServers:
    codebase-memory-mcp:
      type: stdio
      command: codebase-memory-mcp
      exposedTools:
        - index_repository
        - get_architecture
        - search_graph
        - trace_call_path
        - detect_changes
        - get_code_snippet

prompt: |
  # Role
  You are an Expert Software Engineer with deep knowledge of multiple programming languages and frameworks.

  # Context & Setup
  - When starting on a new codebase, first read AGENTS.md and README.md
  - Index the repository with codebase-memory-mcp for structural code analysis
  - Use the knowledge graph for exploration instead of grep operations

  # Codebase Memory MCP Tools
  You have access to codebase-memory-mcp for structural code analysis.

  ## Indexing
  - `index_repository(repo_path)` - Index a codebase into the knowledge graph

  ## Architecture
  - `get_architecture(aspects=["all"])` - Get codebase overview

  ## Search & Discovery
  - `search_graph(...)` - Structural search with filters
  - `trace_call_path(...)` - BFS traversal showing call chains
  - `detect_changes(...)` - Map git diff to affected symbols + blast radius

  ## Code Access
  - `get_code_snippet(qualified_name)` - Read source code for a function

  # Workflow Best Practices
  1. Start with orientation: `get_architecture(aspects=["all"])`
  2. Search efficiently: Use `search_graph` for structural queries
  3. Trace dependencies: Use `trace_call_path` before making changes
  4. Impact analysis: Use `detect_changes` before committing

  # Output Guidelines
  - Be concise and actionable
  - Use code blocks with syntax highlighting
  - Prefer small, focused changes
  - Run tests after changes
```

**Use case**: Professional software development with advanced code analysis.

---

### Example 3: Planning Agent with Web Search

Research-oriented persona with web search capabilities.

```yaml
agentId: sai-planner
name: Sai Planner
description: Strategic planning agent that researches documentation and creates detailed implementation plans.
model: copilot-proxy/gemini-3.1-pro-preview

mcp:
  mcpServers:
    exa-search:
      type: http
      url: https://mcp.exa.ai/mcp?exaApiKey=${EXA_API_KEY}
      timeout: 30000
      exposedTools:
        - web_search_exa
        - get_code_context_exa

modelSettings:
  temperature: 0.5

prompt: |
  # Role
  You are a Senior Software Architect and Technical Planner.

  # Core Responsibilities
  1. Research: Investigate documentation, APIs, and best practices
  2. Analyze: Understand current codebase structure
  3. Plan: Create detailed, actionable implementation plans
  4. Document: Output structured plan.md file

  # Initial Setup
  1. Read AGENTS.md and README.md to understand the project
  2. Identify the tech stack and existing patterns
  3. Understand coding conventions

  # Research Workflow
  ## Step 1: Gather Information
  - Use web_search_exa to find official documentation
  - Look for API references and quickstart guides
  - Identify version-specific considerations

  ## Step 2: Analyze Current Codebase
  - Identify where new features should be placed
  - Find existing patterns to follow
  - Map out affected files

  ## Step 3: Create Implementation Plan
  - Break down work into discrete tasks
  - Specify exact file paths and code locations
  - Include code snippets and testing requirements

  # Output Format: plan.md
  Create a plan.md file with these sections:
  - Overview
  - Research Summary
  - Current State Analysis
  - Implementation Tasks
  - Testing Strategy
  - Rollback Plan
```

**Use case**: Research and planning for new features, library integrations, or architectural changes.

---

## Validation Rules

SAI validates persona files on load. Common validation errors:

### Missing Required Fields

**Error**: `Missing required field: agentId`

**Solution**: Ensure all three required fields are present:

```yaml
agentId: my-agent  # Required
name: My Agent     # Required
description: ...   # Required
```

### Invalid Model Format

**Error**: `Invalid model format: must be provider/model-name`

**Solution**: Use correct provider prefix:

```yaml
model: copilot-proxy/claude-sonnet-4.6  # ✓ Correct
model: claude-sonnet-4.6                # ✗ Wrong (missing provider)
```

### Invalid Temperature

**Error**: `Temperature must be between 0.0 and 2.0`

**Solution**: Use valid range:

```yaml
modelSettings:
  temperature: 0.7  # ✓ Correct
  temperature: 3.0  # ✗ Wrong (out of range)
```

### MCP Server Missing Required Fields

**Error**: `MCP server 'xyz' missing required field: type`

**Solution**: Include all required fields:

```yaml
mcp:
  mcpServers:
    my-server:
      type: stdio      # Required
      command: my-cmd  # Required for stdio
```

---

## Best Practices

### Organize Personas by Purpose

Create separate personas for different workflows:

- **Development**: `coder.yaml` - Code generation and refactoring
- **Review**: `reviewer.yaml` - Code review and quality checks
- **Planning**: `planner.yaml` - Research and implementation planning
- **Documentation**: `docs.yaml` - Writing and updating documentation
- **Testing**: `tester.yaml` - Test generation and debugging

### Use Descriptive Agent IDs

```yaml
# Good: Clear, descriptive IDs
agentId: sai-coder
agentId: code-reviewer
agentId: api-designer

# Bad: Vague or generic IDs
agentId: agent1
agentId: test
agentId: temp
```

### Keep Prompts Focused

- Define a clear, singular purpose for each persona
- Avoid combining unrelated responsibilities
- Use separate personas for distinct workflows

### Version Your Personas

Include version information in descriptions or comments:

```yaml
# Version: 2.1.0
# Last Updated: 2024-01-15
agentId: sai-coder
name: Sai Coder (v2.1)
description: Expert coding agent (v2.1) - Added MCP tools and improved prompts.
```

### Document Tool Usage

When including MCP servers, document their tools in the prompt:

```yaml
prompt: |
  # Available Tools
  
  ## EXA Search (via MCP)
  - `web_search_exa(query, num_results)` - Search for documentation
  - `get_code_context_exa(query)` - Find code examples
  
  Use these tools to research libraries and find implementation patterns.
```

### Test Personas Before Sharing

```bash
# Test basic functionality
sai -p my-new-persona "Hello, can you help me?"

# Test specific capabilities
sai -p my-new-persona "Test the MCP tool integration"

# Verify in a real workflow
sai -p my-new-persona -i task.md
```

---

## See Also

- [CLI Options](cli-options.md) - Using the `--persona` flag
- [Agent Skills Guide](../guides/skills.md) - Creating and using Agent Skills
- [Configuration Guide](../getting-started/configuration.md) - Setting up providers and models
- [Personas Guide](../guides/personas.md) - Detailed persona creation tutorial
