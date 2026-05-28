# Running SAI

!!! info "Coming Soon"
    This page is under construction. Check back soon for detailed guides on running SAI in different modes.

## Quick Reference

SAI supports multiple modes of operation:

### Interactive Mode

```bash
java -jar target/sai-1.0-SNAPSHOT.jar
```

Start a conversational session with the AI agent.

#### Interactive Features

- **Ctrl-C to Interrupt**: Press Ctrl-C at any time to cancel a running agent task
  - Gracefully stops the current operation
  - Returns immediately to the input prompt
  - Allows you to start a new query without waiting
  - No need to kill the process

- **Slash Commands**: Control session settings with `/` commands
  ```
  > /model                        # show current model
  > /model openai/gpt-4           # switch model
  > /persona reviewer             # load a persona
  > /skills                       # list available skills
  > /help                         # list all slash commands
  ```

- **Shell Commands**: Execute shell commands with `!` prefix

- **Exit**: Type `exit` to quit the application

### Single-Input Mode

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input "Your query here"
```

Execute one command and exit.

### Pipe Mode

```bash
echo "What can you do?" | java -jar target/sai-1.0-SNAPSHOT.jar
```

Pipe input directly to SAI.

### Headless Mode

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --headless < prompts.txt
```

Process multiple inputs from a file.

## Session Management

### List Sessions

```bash
java -jar target/sai-1.0-SNAPSHOT.jar list-sessions
```

### Resume Session

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --session-id <session-id>
```

### Export Session

```bash
java -jar target/sai-1.0-SNAPSHOT.jar export-session <session-id> output.md
```

## More Information

- [Installation](../getting-started/installation.md)
- [Configuration](../getting-started/configuration.md)
- [Quick Start](../getting-started/quickstart.md)
