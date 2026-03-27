# Personas

Personas allow you to customize SAI's behavior, system prompt, and model configuration for different tasks. Think of them as specialized configurations for different use cases.

## What are Personas?

A persona is a YAML or JSON file that defines:

- **Identity** - Agent ID, name, and description
- **Model** - Which AI model to use
- **Behavior** - System prompt and instructions
- **Settings** - Temperature, max tokens, etc.
- **Skills** - Which skills to enable
- **Tools** - Additional capabilities

## Basic Example

Here's a simple persona file (`~/.config/sai/persona/helper.yaml`):

```yaml
agentId: helper
name: Helpful Assistant
description: A general-purpose assistant
model: openai/gpt-4o
prompt: |
  You are a helpful assistant.
  Be concise and actionable.
  Prefer bullet points for multi-step answers.
```

Use it:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona helper
```

## Persona File Format

### Required Fields

```yaml
agentId: unique-id          # Unique identifier
name: Display Name          # Human-readable name
description: Brief desc     # What this persona does
```

### Optional Fields

```yaml
model: openai/gpt-4o       # Override default model

modelSettings:              # Model-specific settings
  temperature: 0.7          # Creativity (0.0-2.0)
  maxTokens: 4096          # Max response length
  topP: 0.9                # Nucleus sampling

prompt: |                   # System prompt
  You are a specialized assistant.
  Your instructions here...

skillDirectories:           # Where to find skills
  - skills
  - /path/to/custom/skills

skillNames:                 # Which skills to load
  - code-review
  - security-audit
```

## Loading Personas

### By Name

Place personas in `~/.config/sai/persona/` and load by name:

```bash
# Looks for ~/.config/sai/persona/reviewer.yaml
java -jar target/sai-1.0-SNAPSHOT.jar --persona reviewer
```

### By Relative Path

Load from current directory:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona ./my-persona.yaml
```

### By Absolute Path

Load from anywhere:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona /path/to/persona.yaml
```

## Example Personas

SAI includes several example personas in `examples/personas/`:

### Basic Assistant

```yaml title="examples/personas/basic.yaml"
agentId: sai-agent
name: Sai Agent
description: Default helpful CLI assistant
model: openai/gpt-4o
prompt: |
  You are a helpful assistant for the command line.
  Be concise and actionable.
  Prefer bullet points for multi-step answers.
```

Usage:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona examples/personas/basic.yaml
```

### Code Reviewer

```yaml title="examples/personas/reviewer.yaml"
agentId: reviewer
name: Code Reviewer
description: Reviews code for quality and best practices
model: openai/gpt-4o
modelSettings:
  temperature: 0.3  # Lower temperature for consistent reviews
prompt: |
  You are an expert code reviewer.
  Focus on:
  - Code quality and maintainability
  - Best practices and conventions
  - Security vulnerabilities
  - Performance issues
  Be constructive and provide examples.
```

Usage:

```bash
git diff | java -jar target/sai-1.0-SNAPSHOT.jar \
  --persona examples/personas/reviewer.yaml \
  --input "Review these changes"
```

### Coding Assistant

For a persona optimized for coding tasks:

```yaml title="~/.config/sai/persona/coder.yaml"
agentId: coder
name: Coding Assistant
description: Helps with software development
model: openai/gpt-4o
modelSettings:
  temperature: 0.7
prompt: |
  You are an expert software engineer.
  When writing code:
  - Follow language best practices
  - Include error handling
  - Add helpful comments
  - Prefer clear over clever
  Read AGENTS.md and README.md to understand the project first.
```

Usage:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar -p coder
```

### Documentation Writer

```yaml title="~/.config/sai/persona/docs.yaml"
agentId: docs-writer
name: Documentation Writer
description: Creates clear, comprehensive documentation
model: openai/gpt-4o
modelSettings:
  temperature: 0.5
prompt: |
  You are a technical documentation expert.
  Write clear, comprehensive documentation with:
  - Step-by-step instructions
  - Code examples
  - Common pitfalls
  - Prerequisites
  Target audience: developers with moderate experience.
```

## Creating Custom Personas

### Step 1: Create the File

```bash
mkdir -p ~/.config/sai/persona
nano ~/.config/sai/persona/my-persona.yaml
```

### Step 2: Define the Persona

```yaml
agentId: my-specialized-agent
name: My Specialized Agent
description: Does specific task X really well

# Choose your model
model: openai/gpt-4o

# Tune the settings
modelSettings:
  temperature: 0.8
  maxTokens: 8192

# Write your system prompt
prompt: |
  You are an expert in [specific domain].
  
  Your approach:
  1. First, understand the context
  2. Then, provide actionable advice
  3. Always explain your reasoning
  
  Guidelines:
  - Be specific and practical
  - Provide examples when helpful
  - Ask clarifying questions if needed
```

### Step 3: Test It

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona my-persona
```

## Model Settings Explained

### Temperature

Controls randomness/creativity:

```yaml
modelSettings:
  temperature: 0.0  # Deterministic, focused (good for code review)
  temperature: 0.7  # Balanced (good for general use)
  temperature: 1.5  # Creative (good for brainstorming)
```

### Max Tokens

Limits response length:

```yaml
modelSettings:
  maxTokens: 2048   # Short responses
  maxTokens: 4096   # Standard
  maxTokens: 8192   # Long, detailed responses
```

### Top P (Nucleus Sampling)

Controls diversity:

```yaml
modelSettings:
  topP: 0.1   # Very focused
  topP: 0.9   # Balanced
  topP: 1.0   # Maximum diversity
```

## Personas with Skills

Enable specific skills for your persona:

```yaml
agentId: security-expert
name: Security Expert
description: Security-focused code review
model: openai/gpt-4o

skillDirectories:
  - ~/.config/sai/skills
  - ./project-skills

skillNames:
  - security-audit
  - vulnerability-scan
  - compliance-check

prompt: |
  You are a security expert.
  Use the available skills to perform thorough security audits.
```

## Switching Models

Override the model at runtime:

```bash
# Use persona's settings but different model
java -jar target/sai-1.0-SNAPSHOT.jar \
  --persona reviewer \
  --model azure/gpt-4-turbo
```

## Best Practices

### Write Specific Prompts

❌ **Bad:**
```yaml
prompt: "You are helpful"
```

✅ **Good:**
```yaml
prompt: |
  You are a helpful coding assistant.
  - Provide working code examples
  - Explain trade-offs
  - Follow Python PEP 8 style
```

### Use Appropriate Temperature

- **Code generation/review:** 0.3-0.5 (low)
- **General assistance:** 0.7-0.9 (medium)
- **Creative writing:** 1.0-1.5 (high)

### Keep Prompts Maintainable

```yaml
prompt: |
  You are [role].
  
  Responsibilities:
  - [Task 1]
  - [Task 2]
  
  Guidelines:
  - [Rule 1]
  - [Rule 2]
```

### Test with Real Tasks

Always test personas with actual use cases:

```bash
# Test code review persona
git diff | java -jar target/sai-1.0-SNAPSHOT.jar -p reviewer

# Test documentation persona
java -jar target/sai-1.0-SNAPSHOT.jar -p docs -i "Document the API"
```

## Persona Locations

SAI looks for personas in these locations (in order):

1. **Exact path** - `/path/to/persona.yaml`
2. **Relative path** - `./personas/custom.yaml`
3. **By name** - `~/.config/sai/persona/<name>.yaml`
4. **Example personas** - `examples/personas/<name>.yaml`

## Command Line Reference

```bash
# Load by name
--persona reviewer
-p reviewer

# Load by path
--persona ./custom.yaml
--persona /absolute/path/to/persona.yaml

# Override model
--persona reviewer --model openai/gpt-4o

# With custom config directory
--config-dir /path/to/config --persona reviewer
```

## Advanced: Environment-Specific Personas

Use environment variables in personas:

```yaml
agentId: env-aware
name: Environment-Aware Agent
model: ${SAI_MODEL:-openai/gpt-4o}  # Fallback to default

prompt: |
  Environment: ${ENVIRONMENT:-development}
  Your instructions here...
```

Then:

```bash
export SAI_MODEL=azure/gpt-4-turbo
export ENVIRONMENT=production
java -jar target/sai-1.0-SNAPSHOT.jar -p env-aware
```

## Troubleshooting

### Persona Not Found

**Error:** `Persona 'reviewer' not found`

**Solutions:**

1. Check the name: `ls ~/.config/sai/persona/`
2. Use full path: `--persona /full/path/to/reviewer.yaml`
3. Check for typos in the filename

### Invalid Persona Format

**Error:** `Failed to parse persona file`

**Solutions:**

1. Validate YAML syntax: `yamllint persona.yaml`
2. Check required fields (agentId, name, description)
3. Ensure proper indentation

### Model Not Available

**Error:** `Model not found: openai/gpt-5`

**Solutions:**

1. Check model name spelling
2. Verify your provider subscription
3. Use `--model` to override: `--persona reviewer --model openai/gpt-4o`

## Next Steps

- [Agent Skills](skills.md) - Extend personas with skills
- [Configuration](../getting-started/configuration.md) - Model provider setup
- [CLI Reference](../reference/cli-options.md) - All command options
- [Persona Format Reference](../reference/persona-format.md) - Complete schema
