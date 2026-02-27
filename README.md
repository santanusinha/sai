# SAI (Sentinel AI) - CLI Agent

SAI is a CLI-based AI agent built on the Sentinel AI framework. It connects to Large Language Models (LLMs) such as GitHub Copilot (via Copilot proxy) or Azure OpenAI to assist you from the command line. It supports interactive sessions, basic tool use, and local session persistence so you can resume where you left off.

- Java 17+
- Maven build producing a single shaded JAR
- Model providers: GitHub Copilot or Azure OpenAI
- Tools: Sentinel AI toolboxes (MCP, Remote HTTP, Embeddings, Filesystem, Sessions)
- Logging via Logback

## Table of Contents
- Prerequisites
- Quickstart
- Configuration
- Running
- Usage
- Logging
- Development Notes
- Project Structure
- Acknowledgements

## Prerequisites
- Java 17 or newer
- Maven 3.8+ (to build from source)
- Network access to your chosen LLM provider
- Credentials for at least one provider:
  - GitHub Copilot Personal Access Token (PAT), or
  - Azure OpenAI endpoint and API key

## Quickstart
1) Clone and enter the project directory.
2) Provide your credentials via environment variables (see Configuration).
   - Optionally create a .env file in the repo root with the variables.
3) Build:
   - mvn clean package
4) Run:
   - java -jar target/sai-1.0-SNAPSHOT.jar

## Configuration
You can set environment variables in your shell or in a .env file at the project root.

Required for the default GitHub Copilot model:
- COPILOT_PAT: Your GitHub Copilot Personal Access Token

Optional / Azure configuration:
- AZURE_GPT5_ENDPOINT: Azure OpenAI GPT-5 endpoint URL
- AZURE_GPT5_MINI_ENDPOINT: Azure OpenAI GPT-5 Mini endpoint URL
- AZURE_API_KEY: Azure OpenAI API key

Notes:
- By default, the application is configured to work with GitHub Copilot when COPILOT_PAT is available. To use Azure OpenAI, provide the AZURE_* variables and ensure the model selection in the app configuration points to Azure.

## Running
Basic run (starts a new session):
- java -jar target/sai-1.0-SNAPSHOT.jar

Resume a session by ID:
- java -jar target/sai-1.0-SNAPSHOT.jar <session-id>

Exit the application by typing:
- exit

## Usage
- Start the application and type your query or instruction at the prompt.
- The agent will process your input, optionally call tools, and stream back results.
- Sessions are stored locally so you can resume by passing the session ID the next time you launch.

## Logging
Logging is configured via src/main/resources/logback.xml. Edit this file to adjust log levels, appenders, and formats. By default, logs print to the console; you can add file appenders if desired.

## Development Notes
- JDK level: 17 (configured via maven.compiler.release)
- Build system: Maven
- Packaging: maven-shade-plugin produces target/sai-1.0-SNAPSHOT.jar with the main class io.appform.sai.App
- Code style/formatting: Spotless is configured.
  - To check formatting: mvn compile (Spotless runs in the compile phase)
  - To apply formatting locally: mvn com.diffplug.spotless:spotless-maven-plugin:apply
- Lombok is used (provided scope). Ensure annotation processing is enabled in your IDE.
- Testing: mvn test

## Project Structure
- pom.xml: Project metadata, dependencies, plugins
- src/main/resources/logback.xml: Logback configuration
- target/: Build artifacts (including the shaded JAR)
- AGENTS.md: Developer-oriented guide and architecture overview

## Acknowledgements
- Sentinel AI framework (core, models, toolboxes, session, filesystem)
- GitHub Copilot client
- Picocli (CLI)
- Logback (logging)
- JLine (terminal interaction)

---
If you need a different README structure or additional sections (screenshots, examples, FAQs, or detailed provider setup), let me know and I can tailor it further.
