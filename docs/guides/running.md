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
