# Interactive Commands

When running SAI in interactive mode, special commands allow you to execute shell commands and control the session without leaving the interface.

## Overview

SAI recognizes two types of input in interactive mode:

1. **Special Commands** - Processed by SAI itself
2. **AI Queries** - Everything else goes to the AI agent

## Shell Command Execution

### Basic Syntax

Prefix any shell command with `!` to execute it:

```bash
SAI > !ls -la
SAI > !git status
SAI > !cat README.md
```

### How It Works

When you type `!<command>`:

1. SAI extracts the command after `!`
2. Executes it in your current shell environment
3. Captures and displays the output
4. Returns to the SAI prompt

### Environment Inheritance

Shell commands inherit your environment:

- Current working directory
- Environment variables
- Shell aliases (if running in an interactive shell)
- PATH and other settings

### Example Session

```
SAI > !pwd
/home/user/projects/sai

SAI > !ls src/
main  test  utils

SAI > Based on those files, what's the project structure?

[AI analyzes the output and responds]
```

## Supported Commands

### File Operations

```bash
SAI > !ls -la
SAI > !cat file.txt
SAI > !head -20 README.md
SAI > !tail -f logs/app.log
SAI > !find . -name "*.java"
```

### Git Operations

```bash
SAI > !git status
SAI > !git log --oneline -5
SAI > !git diff
SAI > !git branch -a
SAI > !git show HEAD
```

### Process Management

```bash
SAI > !ps aux | grep java
SAI > !top -b -n 1 | head -20
SAI > !df -h
SAI > !free -m
```

### Text Processing

```bash
SAI > !grep -r "TODO" src/
SAI > !wc -l src/**/*.java
SAI > !sed -n '10,20p' file.txt
```

### Custom Scripts

```bash
SAI > !./scripts/analyze.sh
SAI > !python3 tools/checker.py
SAI > !make test
```

## Exit Command

### Basic Usage

Exit the SAI session:

```bash
SAI > exit
Goodbye!
```

### Important Notes

- Use `exit` **without** the `!` prefix
- The command `!exit` would try to execute the shell's exit command (different behavior)
- Ctrl+C also exits but may leave the session in an inconsistent state

## Command Examples

### Code Analysis Workflow

```bash
SAI > !git diff HEAD~1
[Shows changes]

SAI > Review these changes for potential issues

[AI reviews the diff output]

SAI > !git log --oneline -10
[Shows recent commits]

SAI > Summarize what these commits are doing
```

### Debugging Workflow

```bash
SAI > !tail -50 logs/error.log
[Shows recent errors]

SAI > Analyze these error messages

[AI provides analysis]

SAI > What commands should I run to diagnose this?

[AI suggests diagnostic commands]

SAI > !ps aux | grep java
SAI > !netstat -tlnp | grep 8080
```

### Project Exploration

```bash
SAI > !find . -type f -name "*.java" | head -20
[Lists Java files]

SAI > !wc -l $(find . -name "*.java")
[Counts lines in Java files]

SAI > Based on this structure, what's the architecture?

[AI analyzes the project structure]
```

## Advanced Usage

### Piping and Redirection

```bash
SAI > !cat file.txt | grep "error" | wc -l
SAI > !ls -la | sort -k5 -n
SAI > !git log --oneline | head -5
```

### Command Substitution

```bash
SAI > !echo "Current user: $(whoami)"
SAI > !cat $(find . -name "pom.xml")
```

### Multiple Commands

Use shell operators:

```bash
SAI > !cd src && ls -la
SAI > !make clean && make build
SAI > !echo "Starting..." && ./run.sh && echo "Done"
```

### Complex Commands

```bash
SAI > !for file in *.java; do echo "$file: $(wc -l < $file) lines"; done
SAI > !find . -type f -name "*.log" -mtime -1 -exec ls -lh {} \;
```

## Best Practices

### Combine Shell and AI

Use shell commands to gather data, then ask the AI to analyze:

```bash
# Gather data
SAI > !git log --oneline --since="1 week ago"
SAI > !git diff --stat HEAD~5

# Ask AI to analyze
SAI > What are the main areas of recent development?
SAI > Are there any patterns in these changes?
```

### Use for Context

Provide context to the AI:

```bash
SAI > !cat pom.xml
SAI > What dependencies does this project use?

SAI > !ls -R src/
SAI > Explain the package structure
```

### Verify AI Suggestions

Test AI-suggested commands:

```bash
SAI > How can I find all TODO comments in Java files?

[AI suggests a command]

SAI > !find . -name "*.java" -exec grep -Hn "TODO" {} \;
[Verifies the suggestion works]
```

### Interactive Development

```bash
# Check current state
SAI > !git status

# Get AI advice
SAI > Should I commit these changes?

# Follow AI guidance
SAI > !git add src/main/App.java
SAI > !git commit -m "feat: add new feature"
```

## Security Considerations

### Command Execution Risk

!!! warning "Important"
    Shell commands execute with your user permissions. Be careful with:
    
    - Destructive operations (`rm`, `mv`, `>`)
    - Commands that modify files
    - Network operations
    - Package installations

### Safe Practices

✅ **Safe:**
```bash
SAI > !ls -la
SAI > !cat README.md
SAI > !git status
```

⚠️ **Use Caution:**
```bash
SAI > !rm -rf temp/
SAI > !sudo systemctl restart service
SAI > !curl -X POST https://api.example.com
```

### Confirm Destructive Actions

```bash
# Ask first
SAI > I want to delete all .log files. What command should I use?

[AI suggests command]

# Review before executing
SAI > !find . -name "*.log" -type f
[Review the list]

# Then execute if safe
SAI > !find . -name "*.log" -type f -delete
```

## Limitations

### What Works

- ✅ Most standard Unix/Linux commands
- ✅ Git operations
- ✅ Custom scripts (if executable)
- ✅ Pipes and redirections
- ✅ Environment variable expansion

### What Doesn't Work

- ❌ Interactive commands (will hang waiting for input)
  - `ssh` (unless with -n flag)
  - `vi`, `nano` (use non-interactive alternatives)
  - `less`, `more` (use `cat` or `head`)
- ❌ Shell built-ins that affect the shell state
  - `cd` (changes directory only for that command)
  - `export` (variables not persisted)
  - `alias` (not persisted to subsequent commands)

### Workarounds

**For `cd`:**
```bash
# Instead of changing directory
SAI > !cd src && ls -la

# Or use absolute paths
SAI > !ls -la /full/path/to/src
```

**For interactive editors:**
```bash
# Instead of vi/nano
SAI > !cat file.txt
SAI > Show me how to change line 10

[AI provides guidance, then you edit outside SAI]
```

**For persistent aliases:**
```bash
# Define in your shell's rc file
# Then restart SAI to inherit them
```

## Comparison with Other Modes

### Interactive Mode

```bash
java -jar target/sai-1.0-SNAPSHOT.jar

SAI > !ls -la
SAI > What files are here?
SAI > !git status
SAI > exit
```

Features:
- Multi-turn conversation
- Mix shell commands and AI queries
- Session persistence

### Pipe Mode

```bash
echo "List Java files" | java -jar target/sai-1.0-SNAPSHOT.jar
```

Features:
- Single query
- No interactive shell commands
- Useful for automation

### Headless Mode

```bash
cat << EOF | java -jar target/sai-1.0-SNAPSHOT.jar --headless
What is this project?
List the main files
EOF
```

Features:
- Multiple queries
- No interactive shell commands
- Batch processing

## Tips and Tricks

### Quick File Inspection

```bash
SAI > !head -20 README.md | tail -10
```

Shows lines 11-20 of README.md

### Search and Analyze

```bash
SAI > !grep -r "ERROR" logs/ | head -20
SAI > What patterns do you see in these errors?
```

### Build and Test

```bash
SAI > !mvn clean test
SAI > Any failures? How should I fix them?
```

### Documentation Generation

```bash
SAI > !cat src/main/java/App.java
SAI > Generate JavaDoc comments for this class
```

### Diff Analysis

```bash
SAI > !git diff main...feature-branch
SAI > Summarize the key changes
```

## Keyboard Shortcuts

While in interactive mode:

- **Ctrl+C** - Interrupt current operation / Exit SAI
- **Ctrl+D** - Send EOF / Exit SAI
- **Up/Down arrows** - Command history (if supported by terminal)
- **Tab** - Auto-completion (if supported by terminal)

## Troubleshooting

### Command Not Found

```bash
SAI > !mycommand
bash: mycommand: command not found
```

**Solutions:**
- Verify the command exists: `which mycommand`
- Check your PATH
- Use absolute path: `!/usr/local/bin/mycommand`

### Permission Denied

```bash
SAI > !./script.sh
Permission denied
```

**Solutions:**
- Make script executable: `chmod +x script.sh`
- Run with interpreter: `!bash script.sh`

### Command Hangs

If a command waits for input:

- Press **Ctrl+C** to interrupt
- Use non-interactive flags: `git add -A` instead of `git add -i`
- Redirect input: `!command < /dev/null`

### Working Directory Confusion

```bash
SAI > !cd /tmp
SAI > !pwd
/home/user/projects/sai  # Not /tmp!
```

**Reason:** Each command runs in a fresh shell environment.

**Solution:** Use compound commands:
```bash
SAI > !cd /tmp && pwd
```

## Next Steps

- [Running SAI](running.md) - Learn about other modes
- [Personas](personas.md) - Customize behavior
- [Configuration](../getting-started/configuration.md) - Set up your environment
