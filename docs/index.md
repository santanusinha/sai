---
hide:
  - navigation
  - toc
---

# SAI - Sentinel AI

<div class="grid cards" markdown>

-   :robot:{ .lg .middle } __Multi-Provider Support__

    ---

    Connect to OpenAI, Azure OpenAI, or GitHub Copilot proxy. Switch providers seamlessly with a single configuration change.

-   :speech_balloon:{ .lg .middle } __Interactive & Headless Modes__

    ---

    Run interactively in your terminal, execute single commands, or pipe inputs for automated workflows.

-   :package:{ .lg .middle } __Session Persistence__

    ---

    All conversations are saved locally. Resume, export, or manage your sessions with built-in commands.

-   :performing_arts:{ .lg .middle } __Personas__

    ---

    Customize agent behavior with YAML/JSON persona files. Create specialized agents for different tasks.

-   :wrench:{ .lg .middle } __Extensible Skills__

    ---

    Extend capabilities with Agent Skills - a standard format for teaching the agent new workflows.

-   :zap:{ .lg .middle } __Lightweight & Fast__

    ---

    Single shaded JAR, Java 17+, no external dependencies. Start chatting in seconds.

-   :lock:{ .lg .middle } __Privacy First__

    ---

    All data stored locally. You control what gets sent to AI providers.

-   :hammer_and_wrench:{ .lg .middle } __Developer Friendly__

    ---

    Maven build, clean architecture, well-documented. Easy to extend and customize.

</div>

## Quick Example

```bash
# Build the project
mvn clean package

# Start interactive mode
java -jar target/sai-1.0-SNAPSHOT.jar

# Use with a persona
java -jar target/sai-1.0-SNAPSHOT.jar --persona reviewer

# One-shot command
java -jar target/sai-1.0-SNAPSHOT.jar --input "Summarize this repository"

# Pipe input
echo "What can you do?" | java -jar target/sai-1.0-SNAPSHOT.jar
```

## Why SAI?

**Flexible Integration** - Works with your existing AI provider subscriptions or proxies

**Local First** - Session data stays on your machine. Full control over your data.

**Extensible** - Add custom skills, personas, and tools without modifying core code.

**Production Ready** - Clean logging, error handling, and session management built-in.

## Get Started

<div class="grid cards" markdown>

-   :material-download:{ .lg .middle } [__Installation__](getting-started/installation/)

    ---

    Get SAI up and running in minutes

-   :material-run-fast:{ .lg .middle } [__Quick Start__](getting-started/quickstart/)

    ---

    Your first interaction with SAI

-   :material-cog:{ .lg .middle } [__Configuration__](getting-started/configuration/)

    ---

    Set up your model provider

-   :material-book-open-variant:{ .lg .middle } [__Running SAI__](guides/running/)

    ---

    Learn about different modes

</div>
