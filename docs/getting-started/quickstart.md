# Quick Start

Get up and running with SAI in under 5 minutes.

## Step 1: Set Up Model Provider

Choose one option:

=== "OpenAI"

    ```bash
    export OPENAI_API_KEY=sk-proj-your_key_here
    ```

=== "Azure"

    ```bash
    export AZURE_ENDPOINT=https://your-resource.openai.azure.com
    export AZURE_API_KEY=your_azure_key
    ```

=== "Copilot Proxy"

    ```bash
    # Start the proxy first
    npm install -g copilot-api
    copilot-api
    
    # No env vars needed - proxy runs on localhost:4141
    ```

!!! tip
    Need detailed setup? See [Configuration Guide](configuration.md)

## Step 2: Build SAI

```bash
cd sai
mvn clean package
```

This creates `target/sai-1.0-SNAPSHOT.jar`

## Step 3: Start Your First Session

```bash
java -jar target/sai-1.0-SNAPSHOT.jar
```

You'll see the SAI prompt:

```
SAI >
```

## Step 4: Try Your First Commands

### Ask a Question

```
SAI > What can you do?
```

### Execute Shell Commands

```
SAI > !ls -la
SAI > !git status
```

### Get Help

```
SAI > Tell me about this repository
```

### Exit

```
SAI > exit
```

## What Just Happened?

1. **Session Created** - SAI created a new session in `~/.local/state/sai/sessions/`
2. **Context Loaded** - Your current directory is included in the context
3. **Conversation Saved** - All messages are persisted locally

## Next: Try Different Modes

### One-Shot Command

Run a single command and exit:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input "Summarize the main files in this repo"
```

### Pipe Input

```bash
echo "What programming language is this project?" | java -jar target/sai-1.0-SNAPSHOT.jar
```

### Use a Persona

Load a pre-configured persona:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona examples/personas/basic.yaml
```

### Resume a Session

List your sessions:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar list-sessions
```

Resume one:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --session-id <session-id>
```

## Common First Tasks

### Code Review

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input "Review the changes in my last commit"
```

### Repository Summary

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --input "Give me an overview of this codebase structure"
```

### Explain Code

```bash
SAI > Explain what the App.java file does
```

### Generate Documentation

```bash
SAI > Write a README section explaining the configuration options
```

## Tips

!!! tip "Use shell alias"
    Add to your `.bashrc`:
    ```bash
    alias sai='java -jar /path/to/sai/target/sai-1.0-SNAPSHOT.jar'
    ```

!!! tip "Enable debug mode"
    See what's happening:
    ```bash
    java -jar target/sai-1.0-SNAPSHOT.jar --debug
    ```

!!! tip "Read from file"
    Use `@` syntax:
    ```bash
    java -jar target/sai-1.0-SNAPSHOT.jar --input @prompt.txt
    ```

## What's Next?

- [Configuration](configuration.md) - Learn about all configuration options
- [Installation](installation.md) - Detailed setup instructions
- [GitHub Repository](https://github.com/santanusinha/sai) - View source code

## Troubleshooting

### No Response from AI

**Check:**

1. Environment variables are set correctly
2. API key is valid and has credits
3. Network connection is working
4. For copilot-proxy: proxy is running

**Debug:**

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --debug --input "test"
```

### Command Not Working

**Remember:**

- Shell commands need `!` prefix: `!ls` not `ls`
- Exit with `exit` not `!exit`
- Everything else goes to the AI

## Getting Help

- [GitHub Discussions](https://github.com/santanusinha/sai/discussions)
- [Report Issues](https://github.com/santanusinha/sai/issues)
