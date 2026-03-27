# CLI Options Reference

This page documents all command-line options available in SAI. Options can be specified when starting SAI to configure its behavior, model selection, session management, and more.

## Quick Reference

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--model` | `-m` | AI model to use | `copilot-proxy/claude-haiku-4.5` |
| `--persona` | `-p` | Persona configuration file | - |
| `--session-id` | `-s` | Session identifier | Auto-generated |
| `--input` | `-i` | Input file or command | - |
| `--skill` | - | Enable specific skill | - |
| `--data-dir` | - | Data directory path | `~/.local/state/sai/` |
| `--config-dir` | - | Config directory path | `~/.config/sai/` |
| `--headless` | - | Run without interactive UI | `false` |
| `--debug` | `-d` | Enable debug logging | `false` |
| `--help` | `-h` | Display help message | - |
| `--version` | `-V` | Display version | - |

## Detailed Options

### Model Selection

#### `--model`, `-m`

Specifies the AI model to use for the session.

**Format:** `<provider>/<model-name>`

**Default:** `copilot-proxy/claude-haiku-4.5`

**Supported Providers:**

- `openai` - OpenAI models (GPT-4, GPT-3.5, etc.)
- `azure` - Azure OpenAI Service
- `copilot-proxy` - GitHub Copilot proxy

**Examples:**

=== "OpenAI GPT-4"
    ```bash
    sai --model openai/gpt-4
    ```

=== "Azure OpenAI"
    ```bash
    sai --model azure/gpt-4
    ```

=== "Copilot Claude"
    ```bash
    sai -m copilot-proxy/claude-sonnet-3.5
    ```

=== "Default Model"
    ```bash
    sai  # Uses copilot-proxy/claude-haiku-4.5
    ```

!!! tip "Model Selection Priority"
    Models are resolved in this order:
    
    1. Command-line `--model` flag
    2. Persona configuration `model` field
    3. Default: `copilot-proxy/claude-haiku-4.5`

---

### Session Management

#### `--session-id`, `-s`

Specifies a session identifier to resume an existing session or create a new one with a specific ID.

**Default:** Auto-generated UUID

**Examples:**

=== "Resume Session"
    ```bash
    sai --session-id abc123-def456
    ```

=== "Named Session"
    ```bash
    sai -s my-project-session
    ```

=== "Auto-generated"
    ```bash
    sai  # Creates new session with random ID
    ```

!!! info "Session Storage"
    Sessions are stored in `${data-dir}/sessions/` with conversation history, context, and metadata.

---

### Persona Configuration

#### `--persona`, `-p`

Loads a persona configuration file (YAML or JSON) to customize the agent's behavior, system prompt, skills, and model settings.

**Format:** Path to YAML or JSON file

**Examples:**

=== "Load Persona"
    ```bash
    sai --persona ~/.config/sai/personas/developer.yaml
    ```

=== "Custom Location"
    ```bash
    sai -p ./my-persona.json
    ```

=== "Relative Path"
    ```bash
    sai -p personas/code-reviewer.yaml
    ```

!!! tip "Persona Priority"
    Persona settings override defaults but are overridden by explicit CLI flags.

See [Persona Format Reference](persona-format.md) for complete schema documentation.

---

### Input Options

#### `--input`, `-i`

Provides input to SAI from a file or as a direct command. Useful for automation and scripting.

**Format:** File path or command string

**Examples:**

=== "File Input"
    ```bash
    sai --input prompt.txt
    ```

=== "Direct Command"
    ```bash
    sai -i "Analyze the main.java file"
    ```

=== "With Headless Mode"
    ```bash
    sai --headless --input "Generate test cases" > output.txt
    ```

!!! warning "Interactive vs Headless"
    When using `--input` without `--headless`, SAI will process the input and then enter interactive mode.

---

### Skill Management

#### `--skill`

Enables a specific Agent Skill for the session. Can be specified multiple times to enable multiple skills.

**Format:** Skill directory name or full path

**Examples:**

=== "Enable Single Skill"
    ```bash
    sai --skill code-analysis
    ```

=== "Multiple Skills"
    ```bash
    sai --skill code-analysis --skill documentation
    ```

=== "Custom Skill Path"
    ```bash
    sai --skill /path/to/custom-skill
    ```

!!! info "Skill Discovery"
    SAI searches for skills in:
    
    1. `${config-dir}/skills/`
    2. Paths specified in persona `skillDirectories`
    3. Absolute paths provided via `--skill`

See [Agent Skills Guide](../guides/skills.md) for more information.

---

### Directory Configuration

#### `--data-dir`

Specifies the directory for storing session data, conversation history, and runtime state.

**Default:** `~/.local/state/sai/`

**Examples:**

=== "Custom Data Directory"
    ```bash
    sai --data-dir /tmp/sai-data
    ```

=== "Project-specific Storage"
    ```bash
    sai --data-dir ./project/.sai
    ```

!!! note "Directory Structure"
    The data directory contains:
    
    ```
    data-dir/
    ├── sessions/          # Session conversation history
    ├── cache/             # Temporary cache files
    └── state/             # Runtime state
    ```

#### `--config-dir`

Specifies the directory for configuration files, personas, and skills.

**Default:** `~/.config/sai/`

**Examples:**

=== "Custom Config Directory"
    ```bash
    sai --config-dir ~/my-sai-config
    ```

=== "Shared Configuration"
    ```bash
    sai --config-dir /etc/sai
    ```

!!! note "Directory Structure"
    The config directory contains:
    
    ```
    config-dir/
    ├── personas/          # Persona YAML/JSON files
    ├── skills/            # Agent Skills directories
    └── config.yaml        # Global configuration
    ```

---

### Execution Modes

#### `--headless`

Runs SAI in headless mode without the interactive terminal UI. Useful for automation, CI/CD, and scripting.

**Default:** `false` (interactive mode)

**Examples:**

=== "Headless Execution"
    ```bash
    sai --headless --input "Generate README.md"
    ```

=== "Pipeline Integration"
    ```bash
    cat task.txt | sai --headless -i "$(cat -)" > result.txt
    ```

=== "Batch Processing"
    ```bash
    for file in *.java; do
      sai --headless -i "Analyze $file" >> analysis.log
    done
    ```

!!! tip "Exit Codes"
    In headless mode, SAI returns:
    
    - `0` - Success
    - `1` - Error occurred
    - `2` - Invalid arguments

---

### Debugging and Information

#### `--debug`, `-d`

Enables debug logging for troubleshooting. Shows detailed information about API calls, skill loading, and internal operations.

**Default:** `false`

**Examples:**

=== "Enable Debug Logging"
    ```bash
    sai --debug
    ```

=== "Short Form"
    ```bash
    sai -d
    ```

=== "With Log File"
    ```bash
    sai --debug 2> debug.log
    ```

!!! info "Debug Output"
    Debug mode includes:
    
    - API request/response details
    - Skill loading and activation
    - Configuration resolution
    - Session state changes

#### `--help`, `-h`

Displays help information and usage instructions.

**Examples:**

=== "Show Help"
    ```bash
    sai --help
    ```

=== "Short Form"
    ```bash
    sai -h
    ```

#### `--version`, `-V`

Displays the SAI version number.

**Examples:**

=== "Show Version"
    ```bash
    sai --version
    ```

=== "Short Form"
    ```bash
    sai -V
    ```

---

## Usage Patterns

### Common Combinations

=== "Development Session"
    ```bash
    sai --persona developer.yaml \
        --skill code-analysis \
        --debug
    ```

=== "Production Script"
    ```bash
    sai --headless \
        --model openai/gpt-4 \
        --input task.txt \
        --data-dir ./sai-data \
        > output.txt
    ```

=== "Resume with Custom Model"
    ```bash
    sai --session-id abc123 \
        --model azure/gpt-4 \
        --persona code-reviewer.yaml
    ```

=== "Quick Query"
    ```bash
    sai -m copilot-proxy/claude-sonnet-3.5 \
        -i "What's in this directory?"
    ```

### Environment Integration

SAI options work seamlessly with environment variables. See [Environment Variables Reference](environment.md) for provider configuration.

=== "OpenAI with Custom Model"
    ```bash
    export OPENAI_API_KEY="sk-..."
    sai --model openai/gpt-4
    ```

=== "Azure with Session"
    ```bash
    export AZURE_ENDPOINT="https://..."
    export AZURE_API_KEY="..."
    sai --model azure/gpt-4 --session-id project-x
    ```

---

## See Also

- [Subcommands Reference](subcommands.md) - Session management commands
- [Environment Variables](environment.md) - Provider configuration
- [Persona Format](persona-format.md) - Persona configuration schema
- [Quick Start Guide](../getting-started/quickstart.md) - Getting started with SAI
