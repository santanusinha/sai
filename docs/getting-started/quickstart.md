# Quick Start

Get up and running with SAI in under 5 minutes.

## Step 1: Install SAI

Run the installer (it handles Java, Maven, and everything else automatically):

```bash
curl -fsSL https://raw.githubusercontent.com/santanusinha/sai/main/sai-installer | bash -s -- install
```

Then reload your shell:

```bash
source ~/.bashrc   # or ~/.zshrc, or open a new terminal
```

!!! tip
    See the [Installation Guide](installation.md) for full details, custom install locations, and the manual build path.

## Step 2: Configure your model provider

The installer creates `~/.config/sai/.env`. Open it and fill in your credentials:

```bash
nano ~/.config/sai/.env
```

Choose one provider:

=== "OpenAI"

    ```bash
    OPENAI_API_KEY=sk-proj-your_key_here
    ```

=== "Azure"

    ```bash
    AZURE_ENDPOINT=https://your-resource.openai.azure.com
    AZURE_API_KEY=your_azure_key
    ```

=== "Copilot Proxy"

    ```bash
    # Start the proxy first (separate terminal)
    npm install -g copilot-api
    copilot-api

    # No env vars needed — proxy runs on localhost:4141 by default
    ```

!!! tip
    Need detailed setup? See the [Configuration Guide](configuration.md).

## Step 3: Start your first session

```bash
sai
```

You'll see the SAI prompt:

```
SAI >
```

## Step 4: Try your first commands

### Ask a question

```
SAI > What can you do?
```

### Execute shell commands

```
SAI > !ls -la
SAI > !git status
```

### Exit

```
SAI > exit
```

## What just happened?

1. **Session created** — SAI created a new session in `~/.local/state/sai/sessions/`
2. **Context loaded** — Your current directory is included in the context
3. **Conversation saved** — All messages are persisted locally

---

## Next: try different modes

### One-shot command

Run a single command and exit:

```bash
sai --input "Summarize the main files in this repo"
```

### Pipe input

```bash
echo "What programming language is this project?" | sai
```

### Use a bundled persona

The installer seeds several ready-to-use personas into `~/.config/sai/persona/`. Load one by name:

```bash
sai --persona coder       # software-engineering assistant
sai --persona reviewer    # code review
sai --persona planner     # planning and task decomposition
sai --persona pr-reviewer # pull-request review
```

### Resume a session

List your sessions:

```bash
sai list-sessions
```

Resume one:

```bash
sai --session-id <session-id>
```

---

## Common first tasks

### Code review

```bash
sai --input "Review the changes in my last commit"
# or pipe git diff directly
git diff | sai --persona reviewer --input "Review these changes"
```

### Repository summary

```bash
sai --input "Give me an overview of this codebase structure"
```

### Explain code

```
SAI > Explain what App.java does
```

### Generate documentation

```
SAI > Write a README section explaining the configuration options
```

---

## Tips

!!! tip "Enable debug mode"
    See what's happening under the hood:
    ```bash
    sai --debug
    ```

!!! tip "Read prompt from a file"
    Use `@` syntax:
    ```bash
    sai --input @prompt.txt
    ```

!!! tip "Upgrade SAI"
    Keep SAI up to date:
    ```bash
    sai-installer upgrade
    ```

---

## What's next?

- [Configuration](configuration.md) — Learn about all configuration options
- [Installation](installation.md) — Detailed setup, custom locations, manual build
- [Personas](../guides/personas.md) — Customize agent behavior
- [Skills](../guides/skills.md) — Extend SAI with bundled and community skills
- [GitHub Repository](https://github.com/santanusinha/sai) — View source code

---

## Troubleshooting

### No response from AI

**Check:**

1. `~/.config/sai/.env` has the right credentials
2. API key is valid and has credits
3. Network connection is working
4. For copilot-proxy: the proxy is running

**Debug:**

```bash
sai --debug --input "test"
```

### Command not working

**Remember:**

- Shell commands need the `!` prefix: `!ls` not `ls`
- Exit with `exit` not `!exit`
- Everything else goes to the AI

## Getting help

- [GitHub Discussions](https://github.com/santanusinha/sai/discussions)
- [Report Issues](https://github.com/santanusinha/sai/issues)
