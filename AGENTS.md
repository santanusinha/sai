# SAI Agent - Developer Guide

SAI (Sentinel AI) is a CLI-based AI agent built using Java and the Sentinel AI framework. It is designed to interact with Large Language Models (LLMs) such as Azure OpenAI or GitHub Copilot to assist users with various tasks directly from the command line.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Building the Project](#building-the-project)
- [Running the Agent](#running-the-agent)
- [Basic Architecture](#basic-architecture)
- [Documentation Maintenance](#documentation-maintenance)

## Prerequisites

Before you begin, ensure you have the following installed:
-   **Java 17** or higher
-   **Maven** (for building the project)

## Configuration

The application requires specific environment variables to function, particularly for connecting to LLM providers. These can be set in a `.env` file in the root directory or exported as system environment variables.

### Required Environment Variables
Based on the default configuration in `App.java`:

-   `COPILOT_PAT`: GitHub Copilot Personal Access Token. This is **required** if using the default GitHub Copilot model configuration.

### Optional / Azure Configuration
If you are configuring the agent to use Azure OpenAI models:
-   `AZURE_GPT5_ENDPOINT`: Endpoint URL for the Azure OpenAI GPT-5 model.
-   `AZURE_GPT5_MINI_ENDPOINT`: Endpoint URL for the Azure OpenAI GPT-5 Mini model.
-   `AZURE_API_KEY`: API Key for authenticating with Azure OpenAI.

## Building the Project

To build the project and generate the executable JAR file, run the following Maven command in the project root:

```bash
mvn clean package
```

This will compile the source code, run tests, and package the application into a shaded JAR file located in the `target/` directory (e.g., `sai-1.0-SNAPSHOT.jar`).

## Running the Agent

Once built, you can run the agent using the `java` command.

### Basic Run
To start a new session:
```bash
java -jar target/sai-1.0-SNAPSHOT.jar
```

### Resuming a Session
To resume a specific session, provide the Session ID as an argument:
```bash
java -jar target/sai-1.0-SNAPSHOT.jar <session-id>
```
*The Session ID is displayed when you start a new session.*

### Interactive Mode
Once running, the agent will prompt you for input:
-   Type your query to interact with the agent.
-   Type `exit` to quit the application.

## Basic Architecture

SAI is built upon the **Sentinel AI** framework and follows a modular architecture:

### Core Components

1.  **App.java**: The main entry point. It handles:
    -   Loading configuration (Environment variables).
    -   Setting up the HTTP client and Model Provider (Azure/Copilot).
    -   Initializing the `SaiAgent`.
    -   Managing the main input loop and event processing.

2.  **SaiAgent**: The core agent logic that coordinates between the user, the LLM, and available tools. It utilizes `AgentSessionExtension` to maintain state across interactions.

3.  **CommandProcessor**: Handles user input commands. It processes the input and delegates it to the agent for execution.

4.  **Tools**: The agent is equipped with tools to perform actions.
    -   `CoreToolBox`: Contains basic system tools (likely file operations, command execution, etc.).
    -   `BashCommandRunner`: A specific tool wrapper for executing bash commands.

5.  **Session Management**:
    -   Sessions are stored locally using `FileSystemSessionStore`.
    -   Data is typically stored in a `sessions/` directory within the configured data path.

6.  **Models**:
    -   The agent uses `SimpleOpenAIModel` to communicate with LLM providers.
    -   It supports switching between providers like Azure OpenAI and GitHub Copilot Proxy.

### Flow
1.  **Startup**: The app initializes, connects to the model provider, and either creates a new session or loads an existing one.
2.  **Input**: User types a command or query.
3.  **Processing**: `CommandProcessor` sends the input to the `SaiAgent`.
4.  **Reasoning**: The LLM processes the query, potentially deciding to call tools (Tool Calling).
5.  **Execution**: If tools are called, the agent executes them (e.g., running a bash command) and feeds the result back to the LLM.
6.  **Response**: The final response is streamed or printed back to the user via the `Printer` or `MessagePrinter`.

## Documentation Maintenance

Keep README.md accurate and in sync with the codebase. When you change any of the following, update the README in the same pull request:

- CLI surface (flags, options, subcommands), default values, or behavior
- Environment variables and provider configuration (e.g., Azure/Copilot endpoints, API keys)
- Session management behavior, data directories, or examples

Recommended workflow:

1. Rebuild the JAR:
   ```bash
   mvn clean package
   ```
2. Refresh the CLI usage block by capturing authoritative help output and pasting it into README.md:
   ```bash
   java -jar target/sai-1.0-SNAPSHOT.jar --help
   ```
3. Verify provider/env var docs reflect the actual names used in App.java and related config.
4. Ensure examples are wrapped in fenced code blocks with appropriate language hints and keep the tone professional. Do not use emojis.
5. In your PR description, include a note such as: "docs(readme): synced with current CLI and configuration".
