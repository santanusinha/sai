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

## Model Configuration

Models are chosen with the model parameter present in the persona file provided and can be overriden using the `--model` command line option.

Format: `<provider>/<model name>`

For example:
- `--model=openai/gpt-5.4` - Command line
- `-m copilot-proxy/claude-sonnet4.6` - Command line
- `model: azure/gpt-4.1` - In the Persona YAML file

If no model is passed anywhere, it defaults to `copilot-proxy/claude-haiku-4.5`.

### Providers
SAI supports the following provider types:
- **openai** - For all openai compliant endpoints including openai, cerebras, openrouter and so on
- **azure** - For azure hosted models
- **copilot-proxy** - If you are ruting your requests through copilot using something like [copilot-api](https://github.com/ericc-ch/copilot-api).

Provider-specific variables:

**OpenAI**
- OPENAI_API_KEY: required
- OPENAI_ENDPOINT: optional, default `https://api.openai.com/v1`
- OPENAI_ORGANIZATION: optional
- OPENAI_PROJECT_ID: optional
- OPENAI_EXTRA_HEADERS: optional, comma-separated `Key:Value` pairs to add to requests

**Azure OpenAI**
- AZURE_ENDPOINT: required (base URL)
- AZURE_API_KEY: required
- AZURE_API_VERSION: optional, default `2024-10-21`

**GitHub Copilot Proxy**
- COPILOT_GITHUB_PAT: required (GitHub token)
- COPILOT_PROXY_ENDPOINT: optional, default `http://localhost:4141`

Example .env file:

```env
OPENAI_API_KEY=your_key_here
# OPENAI_ENDPOINT=https://api.openai.com/v1
# OPENAI_ORGANIZATION=org_...
# OPENAI_PROJECT_ID=proj_...
# OPENAI_EXTRA_HEADERS=Helicone-Auth:Bearer xyz,Another-Header:abc
```

Switching to Azure:

```env
AZURE_ENDPOINT=https://your-azure-openai-endpoint
AZURE_API_KEY=your_azure_key
# AZURE_API_VERSION=2024-10-21
```

Using a Copilot proxy:

```env
# Set the below endpoint only if you are running the proxy at a different endpoint
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
Usage: sai [-dhV] [--headless] [-m[=<model>]] [--config-dir=<configDir>]
           [--data-dir=<dataDir>] [-i=<input>] [-p=<persona>] [-s=<sessionId>]
           [COMMAND]
Sai AI Agent
      --config-dir=<configDir>
                             Override config directory
  -d, --debug                Enable debug mode
      --data-dir=<dataDir>   Override data directory
  -h, --help                 Show this help message and exit.
      --headless             Run in headless mode
  -i, --input=<input>        Execute a single input and exit. If the value
                               starts with '@', read input from the specified
                               file.
  -m, --model[=<model>]      Model to use, in the format 'provider/model' (e.g.
                               'copilot-proxy/claude-haiku-4.5'). Overrides
                               model specified in persona file.
  -p, --persona=<persona>    Path to AgentConfig persona file (.yaml/.yml/.json)
  -s, --session-id=<sessionId>
                             Resume a specific session
  -V, --version              Print version information and exit.
Commands:
  list-sessions    List available sessions
  delete-sessions  Delete a session
  prune-sessions   Prune older sessions. Provide a duration string like '1d',
                     '3h', '30m'
  export-session   Export a session to a markdown file
  summary          Show detailed summary of a specific session
```

Subcommands:

- list-sessions
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar list-sessions
  ```
  Lists sessions found under the configured data directory. Honors `--data-dir` when provided.

- summary
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar summary <session-id>
  ```
  Shows a detailed summary for a specific session. Honors `--data-dir` when provided.

- prune-sessions
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar prune-sessions 1d
  ```
  Prunes older sessions based on duration (e.g., 1d, 3h, 30m). Honors `--data-dir` when provided.

- delete-sessions
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar delete-sessions <session-id>
  ```
  Deletes a session directory by ID. Honors `--data-dir` when provided.

- export-session
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar export-session <session-id> [output-file]
  ```
  Exports a session to a markdown file. If no output file is specified, the markdown is printed to stdout. Honors `--data-dir` when provided.

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

- Export a session to markdown:
  ```bash
  # Print to stdout
  java -jar target/sai-1.0-SNAPSHOT.jar export-session 2f1e4f7a-...-a1b2
  
  # Export to a file
  java -jar target/sai-1.0-SNAPSHOT.jar export-session 2f1e4f7a-...-a1b2 session.md
  ```

## Data and Sessions

- Default data directory: `~/.local/state/sai`
- Default config directory: `~/.config/sai`
- Persona files directory: `~/.config/sai/persona/` (for `-p <name>` lookup)
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
