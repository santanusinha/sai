# SAI (Sentinel AI) - CLI Agent

SAI is a command-line AI agent built on the Sentinel AI framework. It connects to model providers like OpenAI, Azure OpenAI, or a GitHub Copilot proxy, supports interactive and headless modes, and persists local sessions so you can resume where you left off.

- Java 17+
- Maven build producing a single shaded JAR
- Model providers: openai, azure, copilot-proxy
- Local session storage with simple session management commands
- Clean terminal UX with streaming output and event printing

## Table of Contents
- Requirements
- Build
- Quick Start
- Configuration
- Running the Agent
- CLI Reference
- Examples
- Data and Sessions
- Security and privacy
- Logging
- Development

## Requirements
- Java 17 or newer
- Maven 3.8+ (to build from source)
- Network access to your chosen model provider

## Build

```bash
mvn clean package
```

This creates a shaded JAR at:

```text
target/sai-1.0-SNAPSHOT.jar
```

## Quick Start

1) Set required environment variables (see Configuration below).
2) Build the JAR:
   ```bash
   mvn clean package
   ```
3) Run interactively:
   ```bash
   java -jar target/sai-1.0-SNAPSHOT.jar
   ```

## Configuration

The agent is configured via environment variables. One variable is always required to choose the provider:

- MODEL_PROVIDER: one of `openai`, `azure`, `copilot-proxy`

Common variables:

- MODEL: model name to use. Defaults to `gemini-3-pro-preview` if not set.

Provider-specific variables:

OpenAI
- OPENAI_API_KEY: required
- OPENAI_ENDPOINT: optional, default `https://api.openai.com/v1`
- OPENAI_ORGANIZATION: optional
- OPENAI_PROJECT_ID: optional
- OPENAI_EXTRA_HEADERS: optional, comma-separated `Key:Value` pairs to add to requests

Azure OpenAI
- AZURE_ENDPOINT: required (base URL)
- AZURE_API_KEY: required
- AZURE_API_VERSION: optional, default `2024-10-21`

GitHub Copilot Proxy
- COPILOT_GITHUB_PAT: required (GitHub token)
- COPILOT_PROXY_ENDPOINT: optional, default `http://localhost:4141`

Example .env file:

```env
MODEL_PROVIDER=openai
MODEL=gpt-4o-mini
OPENAI_API_KEY=your_key_here
# OPENAI_ENDPOINT=https://api.openai.com/v1
# OPENAI_ORGANIZATION=org_...
# OPENAI_PROJECT_ID=proj_...
# OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer xyz,Another-Header:abc
```

Switching to Azure:

```env
MODEL_PROVIDER=azure
MODEL=gpt-4o-mini
AZURE_ENDPOINT=https://your-azure-openai-endpoint
AZURE_API_KEY=your_azure_key
# AZURE_API_VERSION=2024-10-21
```

Using a Copilot proxy:

```env
MODEL_PROVIDER=copilot-proxy
MODEL=claude-3.5-sonnet
COPILOT_GITHUB_PAT=ghp_...
# COPILOT_PROXY_ENDPOINT=http://localhost:4141
```

## Running the Agent

Interactive mode (new session):

```bash
java -jar target/sai-1.0-SNAPSHOT.jar
```

Start with a persona file (YAML/JSON):

```bash
# Load persona by name from ~/.config/sai/persona/
java -jar target/sai-1.0-SNAPSHOT.jar --persona reviewer

# Load from relative path (resolved from current directory)
java -jar target/sai-1.0-SNAPSHOT.jar --persona examples/personas/basic.yaml

# Load from absolute path
java -jar target/sai-1.0-SNAPSHOT.jar --persona /path/to/custom-persona.yaml
```

Resume an existing session by ID:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --session-id <session-id>
```

Single-input mode (run once and exit):

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input "Summarize the repository"
```

Read input from a file using @-syntax:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input @prompt.txt
```

Headless mode reading from stdin (process each line until EOF or `exit`):

```bash
echo "What can you do?" | java -jar target/sai-1.0-SNAPSHOT.jar --headless
```

Pipe input directly without any flags (stdin is detected automatically):

```bash
echo "Summarize this repo" | java -jar target/sai-1.0-SNAPSHOT.jar
```

Override the data directory:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --data-dir /path/to/state
```

Override the config directory:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --config-dir /path/to/config
```

## Interactive CLI Commands

While in interactive mode, the following special commands are available directly in the prompt. They are processed by SAI itself and are **not** forwarded to the AI agent.

| Command       | Description                                              |
|---------------|----------------------------------------------------------|
| `!<cmd>`      | Execute a shell command (e.g. `!ls -la`, `!git status`) |
| `exit`        | Exit the application                                     |

### Shell execution (`!`)

Prefix any shell command with `!` to run it in your current environment without leaving SAI:

```text
> !ls -la
> !git log --oneline -5
> !cat /etc/os-release
```

Standard output is printed on success; the exit code and stderr are shown on failure.

## CLI Reference

Help output:

```text
Usage: sai [-dhV] [--headless] [--config-dir=<configDir>] [--data-dir=<dataDir>]
           [-i=<input>] [-p=<persona>] [-s=<sessionId>] [COMMAND]
Sai AI Agent
  -d, --debug                Enable debug mode
      --config-dir=<configDir>
                             Override config directory
      --data-dir=<dataDir>   Override data directory
  -h, --help                 Show this help message and exit.
      --headless             Run in headless mode
  -i, --input=<input>        Execute a single input and exit. If the value
                               starts with '@', read input from the specified
                               file.
  -p, --persona=<persona>    Persona name or path to AgentConfig file (.yaml/.yml/.json).
                               If a simple name (e.g., 'reviewer'), looks in
                               {configDir}/persona/ with auto-extension.
                               Relative paths resolve from current directory.
                               Absolute paths are used directly.
  -s, --session-id=<sessionId>
                             Resume a specific session
  -V, --version              Print version information and exit.
Commands:\n  list           List available sessions\n  summary        Show detailed summary for a session\n  prune-sessions Prune older sessions based on duration (e.g., 1d)\n  delete         Delete a session
```

Subcommands:

- list-sessions\n  ```bash\n  java -jar target/sai-1.0-SNAPSHOT.jar list-sessions-sessions\n  ```\n  Lists sessions found under the configured data directory. Honors `--data-dir` when provided.\n\n- summary\n  ```bash\n  java -jar target/sai-1.0-SNAPSHOT.jar summary <session-id>\n  ```\n  Shows a detailed summary for a specific session. Honors `--data-dir` when provided.\n\n- prune-sessions\n  ```bash\n  java -jar target/sai-1.0-SNAPSHOT.jar prune-sessions 1d\n  ```\n  Prunes older sessions based on duration (e.g., 1d, 3h, 30m). Honors `--data-dir` when provided.\n\n- delete-sessions\n  ```bash\n  java -jar target/sai-1.0-SNAPSHOT.jar delete-sessions <session-id>\n  ```\n  Deletes a session directory by ID. Honors `--data-dir` when provided.
## Examples

- Start a new interactive session:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar
  ```

- Start with a persona file (YAML/JSON):
  ```bash
  # By name (looks in ~/.config/sai/persona/)
  java -jar target/sai-1.0-SNAPSHOT.jar -p reviewer
  
  # By relative path
  java -jar target/sai-1.0-SNAPSHOT.jar -p examples/personas/basic.yaml
  
  # By absolute path
  java -jar target/sai-1.0-SNAPSHOT.jar -p /path/to/persona.yaml
  ```

- Resume a session and continue chatting:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar -s 2f1e4f7a-...-a1b2
  ```

- One-off input (no session persisted):
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar -i "List the key modules in this repo"
  ```

- Persona with a one-off input:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar -p examples/personas/basic.yaml -i "What can you do?"
  ```

- Headless with multiple inputs from a file:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar --headless < prompts.txt
  ```

- Single-shot via pipe (no flags needed — stdin piped automatically):
  ```bash
  echo "List the key modules in this repo" | java -jar target/sai-1.0-SNAPSHOT.jar
  ```

- List sessions:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar list-sessions
  ```

- Delete a session:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar delete-sessions 2f1e4f7a-...-a1b2
  ```

## Data and Sessions

- Default data directory: `~/.local/state/sai`
- Default config directory: `~/.config/sai`
- **Persona files directory**: `~/.config/sai/persona/` (for `-p <name>` lookup)
- Sessions are stored under: `<dataDir>/sessions/<sessionId>`
- You can override the data directory with `--data-dir`.
- You can override the config directory with `--config-dir`.
- When using `--input`, the agent runs a one-off request and exits; session persistence is not enabled for this mode.
- When stdin is piped (e.g. `echo "..." | sai`), the piped content is automatically read and treated as a single-shot `--input`; no flags are required.
- In `--headless` mode without `--input`, input is read line-by-line from stdin until EOF or `exit`.

## Security and privacy

- Persona-defined HTTP tools can make network requests. Only use trusted personas and endpoints to avoid SSRF or data exfiltration.
- Prompt context: the agent includes the current working directory name in its system prompt to give models context.

## Logging

Logging is configured via `src/main/resources/logback.xml` and is initialized on startup. Adjust levels and appenders as needed.

## Development

- Java release level: 17
- Build system: Maven
- Packaging: shaded JAR with main class `io.appform.sai.App`
- Code style: Spotless is configured via `spotless-maven-plugin`

Contributions and issues are welcome.
