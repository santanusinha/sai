# SAI Agent - Developer Guide

SAI (Sentinel AI) is a CLI-based AI agent built in Java on the Sentinel AI framework. It interacts with Large Language Models (LLMs) via configurable providers (Azure OpenAI, OpenAI, or a GitHub Copilot Proxy) to assist users from the command line.

## Table of Contents
- Prerequisites
- Configuration
- Building the Project
- Running the Agent
- Basic Architecture
- Documentation Maintenance

## Prerequisites

Ensure you have the following installed:
- Java 17 or higher
- Maven

## Configuration

The agent is configured via environment variables. Set them in your shell or in a `.env` file in the project root.

### Provider selection

- MODEL_PROVIDER: Required. Selects the model provider.
  - Supported values: `azure`, `openai`, `copilot-proxy`
- MODEL: Optional. Specifies the model name to use.
  - Default: `gemini-3-pro-preview` (override as appropriate for your chosen provider)

### Azure OpenAI configuration

- AZURE_ENDPOINT: Required. Base URL for your Azure OpenAI resource, for example: `https://<resource>.openai.azure.com`.
- AZURE_API_KEY: Required. API key for the Azure OpenAI resource.
- AZURE_API_VERSION: Optional. API version; defaults to `2024-10-21`.

### OpenAI configuration

- OPENAI_API_KEY: Required. OpenAI API key.
- OPENAI_ENDPOINT: Optional. Base URL; defaults to `https://api.openai.com/v1`.
- OPENAI_ORGANIZATION: Optional. Organization ID.
- OPENAI_PROJECT_ID: Optional. Project ID.
- OPENAI_EXTRA_HEADERS: Optional. Comma-separated list of additional headers to include with requests, in the form `Header-Name: value,Another-Header: value`.

### GitHub Copilot Proxy configuration

- COPILOT_GITHUB_PAT: Required. GitHub Personal Access Token used by the Copilot proxy.
- COPILOT_PROXY_ENDPOINT: Optional. Base URL for the proxy; defaults to `http://localhost:4141`.

## Building the Project

Build the shaded JAR:

```bash
mvn clean package
```

The artifact will be generated at `target/sai-1.0-SNAPSHOT.jar`.

## Running the Agent

Use the `java -jar` command to launch the agent.

### CLI usage

```text
Usage: sai [-dhV] [--headless] [--data-dir=<dataDir>] [-p=<prompt>]
           [-s=<sessionId>] [COMMAND]
Sai AI Agent
  -d, --debug                Enable debug mode
      --data-dir=<dataDir>   Override data directory
  -h, --help                 Show this help message and exit.
      --headless             Run in headless mode
  -p, --prompt=<prompt>      Execute a single prompt and exit
  -s, --session-id=<sessionId>
                             Resume a specific session
  -V, --version              Print version information and exit.
Commands:
  list    List available sessions
  delete  Delete a session
```

### Examples

- Start a new interactive session:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar
  ```

- Execute a single prompt and exit:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar --prompt "Summarize this project in three bullet points"
  ```

- Resume a specific session:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar --session-id <session-id>
  ```

- List sessions:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar list
  ```

- Delete a session:
  ```bash
  java -jar target/sai-1.0-SNAPSHOT.jar delete
  ```

## Basic Architecture

SAI leverages Sentinel AI components and follows a modular design.

- SaiCommand: Picocli-based CLI entrypoint. Reads environment, configures provider, and starts interactive or single-prompt execution.
- ConfigurableDefaultChatCompletionFactory: Builds ChatCompletionServices for the selected provider (`azure`, `openai`, or `copilot-proxy`) using environment variables.
- SimpleOpenAIModel: Wraps provider-specific ChatCompletionServices and model options.
- CommandProcessor: Processes user input, delegates to the agent, and manages tool invocation.
- Tools:
  - CoreToolBox: Basic system/file/tools
  - BashCommandRunner: Executes shell commands
- Session Management:
  - Uses a file-system backed store to persist conversations and usage statistics.
- Printers:
  - MessagePrinter / EventPrinter: Stream and render agent/LLM responses and events.

## Documentation Maintenance

Keep README.md and AGENTS.md accurate and in sync with the codebase. When you change any of the following, update documentation in the same pull request:

- CLI surface (flags, options, subcommands), default values, or behavior
- Environment variables and provider configuration (Azure/OpenAI/Copilot Proxy)
- Session management behavior, data directories, or examples

Recommended workflow:

1. Rebuild the JAR:
   ```bash
   mvn clean package
   ```
2. Refresh the CLI usage block by capturing authoritative help output and pasting it into README.md and AGENTS.md:
   ```bash
   java -jar target/sai-1.0-SNAPSHOT.jar --help
   ```
3. Verify provider/env var docs reflect the actual names used in SaiCommand and ConfigurableDefaultChatCompletionFactory.
4. Ensure examples use fenced code blocks with language hints and keep the tone professional. Do not use emojis.
5. In your PR description, include a note such as: "docs: synced with current CLI and configuration".
