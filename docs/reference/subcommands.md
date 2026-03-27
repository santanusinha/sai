# Subcommands

SAI provides several subcommands for session management and inspection. All subcommands honor the `--data-dir` flag to override the default data directory (`~/.local/state/sai/`).

## Session Management Commands

### list-sessions

List all sessions with their metadata, sorted by last modified time (most recent first).

**Usage:**

```bash
sai list-sessions [--data-dir=<path>]
```

**Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--data-dir` | Override default data directory | `~/.local/state/sai/` |

**Output Format:**

The command displays sessions in a table format with the following columns:

- **Session ID**: Unique identifier for the session
- **Created**: Timestamp when the session was created
- **Last Modified**: Timestamp of the last interaction
- **Messages**: Number of messages in the session
- **Persona**: Persona used in the session (if any)
- **Model**: LLM model used in the session

**Examples:**

```bash
# List all sessions using default data directory
sai list-sessions

# List sessions from a custom data directory
sai list-sessions --data-dir=/custom/path/to/data
```

**Sample Output:**

```
Session ID              Created              Last Modified        Messages  Persona      Model
----------------------------------------------------------------------------------------------
docs-2024-01-15        2024-01-15 09:30:15  2024-01-15 14:22:30  42        sai-coder    copilot-proxy/claude-sonnet-4.6
review-abc123          2024-01-14 16:45:00  2024-01-15 10:15:20  15        sai-reviewer copilot-proxy/gpt-4o
testing-session        2024-01-13 11:20:10  2024-01-14 08:30:45  8         sai-agent    copilot-proxy/claude-haiku-4.5
```

---

### summary

Display a summary of a specific session, including metadata, message count, persona information, and the first/last messages.

**Usage:**

```bash
sai summary --session-id=<session-id> [--data-dir=<path>]
```

**Options:**

| Option | Description | Required | Default |
|--------|-------------|----------|---------|
| `-s, --session-id` | Session ID to summarize | Yes | - |
| `--data-dir` | Override default data directory | No | `~/.local/state/sai/` |

**Output:**

The command displays:

- Session metadata (ID, created time, last modified time)
- Message count and conversation statistics
- Persona and model information
- Preview of the first user message
- Preview of the last assistant response

**Examples:**

```bash
# Display summary for a specific session
sai summary --session-id=docs-2024-01-15

# Summary with custom data directory
sai summary -s review-abc123 --data-dir=/custom/path/to/data
```

**Sample Output:**

```
Session Summary: docs-2024-01-15
================================
Created:        2024-01-15 09:30:15
Last Modified:  2024-01-15 14:22:30
Messages:       42 (21 user, 21 assistant)
Persona:        sai-coder
Model:          copilot-proxy/claude-sonnet-4.6

First Message (user, 2024-01-15 09:30:15):
-----------------------------------------
Create comprehensive CLI reference documentation for the SAI documentation site...

Last Message (assistant, 2024-01-15 14:22:30):
---------------------------------------------
I've successfully created all four CLI reference pages and updated the navigation...
```

---

### delete-sessions

Delete one or more sessions by their session IDs.

**Usage:**

```bash
sai delete-sessions --session-id=<id1> [--session-id=<id2>...] [--data-dir=<path>]
```

**Options:**

| Option | Description | Required | Default |
|--------|-------------|----------|---------|
| `-s, --session-id` | Session ID(s) to delete (can be specified multiple times) | Yes | - |
| `--data-dir` | Override default data directory | No | `~/.local/state/sai/` |

**Behavior:**

- Permanently deletes the specified session(s) from the data directory
- Removes all messages, metadata, and associated files
- Displays confirmation of deleted sessions
- Fails gracefully if a session ID doesn't exist

**Examples:**

```bash
# Delete a single session
sai delete-sessions --session-id=old-session-123

# Delete multiple sessions at once
sai delete-sessions -s session1 -s session2 -s session3

# Delete sessions from custom data directory
sai delete-sessions --session-id=test-session --data-dir=/custom/path/to/data
```

**Sample Output:**

```
Deleted session: old-session-123
Deleted session: test-session-456
Successfully deleted 2 session(s)
```

!!! warning "Destructive Operation"
    This command permanently deletes session data and cannot be undone. Consider using `export-session` to create a backup before deletion.

---

### prune-sessions

Automatically delete sessions older than a specified duration to reclaim disk space.

**Usage:**

```bash
sai prune-sessions --older-than=<duration> [--data-dir=<path>] [--dry-run]
```

**Options:**

| Option | Description | Required | Default |
|--------|-------------|----------|---------|
| `--older-than` | Delete sessions not modified within this duration | Yes | - |
| `--data-dir` | Override default data directory | No | `~/.local/state/sai/` |
| `--dry-run` | Show what would be deleted without actually deleting | No | `false` |

**Duration Format:**

Durations are specified using a combination of:

- **d** = days (e.g., `7d` = 7 days)
- **h** = hours (e.g., `24h` = 24 hours)
- **m** = minutes (e.g., `30m` = 30 minutes)

You can combine units: `1d12h` = 1 day and 12 hours

**Common Duration Examples:**

| Duration | Meaning |
|----------|---------|
| `7d` | 7 days |
| `30d` | 30 days (approximately 1 month) |
| `90d` | 90 days (approximately 3 months) |
| `24h` | 24 hours (1 day) |
| `1d12h` | 1 day and 12 hours |
| `2d6h30m` | 2 days, 6 hours, and 30 minutes |

**Examples:**

```bash
# Delete sessions older than 30 days
sai prune-sessions --older-than=30d

# Delete sessions older than 7 days (with dry-run preview)
sai prune-sessions --older-than=7d --dry-run

# Delete sessions older than 24 hours from custom directory
sai prune-sessions --older-than=24h --data-dir=/custom/path/to/data

# Delete sessions older than 90 days (quarterly cleanup)
sai prune-sessions --older-than=90d
```

**Sample Output:**

```bash
# Dry-run output
$ sai prune-sessions --older-than=30d --dry-run

[DRY RUN] Would delete 5 session(s):
  - old-session-2023-12-01 (45 days old)
  - test-session-2023-11-28 (48 days old)
  - review-2023-11-25 (51 days old)
  - docs-2023-11-20 (56 days old)
  - archive-2023-11-15 (61 days old)

Total space that would be reclaimed: 12.4 MB

# Actual deletion output
$ sai prune-sessions --older-than=30d

Deleted 5 session(s) older than 30 days:
  - old-session-2023-12-01
  - test-session-2023-11-28
  - review-2023-11-25
  - docs-2023-11-20
  - archive-2023-11-15

Space reclaimed: 12.4 MB
```

!!! tip "Best Practices"
    - Use `--dry-run` first to preview what will be deleted
    - Consider exporting important sessions before pruning
    - Set up regular pruning (e.g., monthly) to manage disk space
    - Common retention periods: 30d (monthly), 90d (quarterly), 180d (semi-annual)

---

### export-session

Export a session to a JSON file for backup, sharing, or analysis.

**Usage:**

```bash
sai export-session --session-id=<session-id> [--output=<file>] [--data-dir=<path>]
```

**Options:**

| Option | Description | Required | Default |
|--------|-------------|----------|---------|
| `-s, --session-id` | Session ID to export | Yes | - |
| `-o, --output` | Output file path | No | `<session-id>.json` |
| `--data-dir` | Override default data directory | No | `~/.local/state/sai/` |

**Output Format:**

The exported JSON file contains:

- **Metadata**: Session ID, creation time, last modified time, persona, model
- **Messages**: Complete conversation history with timestamps, roles, and content
- **Configuration**: Model settings, persona configuration used during the session
- **Statistics**: Message counts, token usage (if available)

**Examples:**

```bash
# Export session with default filename (session-id.json)
sai export-session --session-id=docs-2024-01-15

# Export session with custom filename
sai export-session -s review-abc123 -o reviews/code-review.json

# Export from custom data directory
sai export-session --session-id=test-session --data-dir=/custom/path --output=backup.json
```

**Sample Output:**

```bash
$ sai export-session --session-id=docs-2024-01-15

Exported session 'docs-2024-01-15' to docs-2024-01-15.json
Size: 124.5 KB (42 messages)
```

**Exported JSON Structure:**

```json
{
  "sessionId": "docs-2024-01-15",
  "created": "2024-01-15T09:30:15Z",
  "lastModified": "2024-01-15T14:22:30Z",
  "persona": {
    "agentId": "sai-coder",
    "name": "Sai Coder",
    "model": "copilot-proxy/claude-sonnet-4.6"
  },
  "messages": [
    {
      "role": "user",
      "content": "Create comprehensive CLI reference documentation...",
      "timestamp": "2024-01-15T09:30:15Z"
    },
    {
      "role": "assistant",
      "content": "I'll help you create comprehensive CLI reference documentation...",
      "timestamp": "2024-01-15T09:31:02Z"
    }
  ],
  "statistics": {
    "messageCount": 42,
    "userMessages": 21,
    "assistantMessages": 21
  }
}
```

**Use Cases:**

- **Backup**: Create archives of important conversations before pruning
- **Sharing**: Share conversation context with team members
- **Analysis**: Analyze conversation patterns, token usage, or model performance
- **Migration**: Move sessions between different SAI installations
- **Documentation**: Extract conversation history for project documentation

!!! tip "Session Archival Workflow"
    ```bash
    # 1. List sessions to identify old ones
    sai list-sessions
    
    # 2. Export important sessions before cleanup
    sai export-session -s important-session-1 -o archives/session-1.json
    sai export-session -s important-session-2 -o archives/session-2.json
    
    # 3. Preview what will be deleted
    sai prune-sessions --older-than=30d --dry-run
    
    # 4. Execute cleanup
    sai prune-sessions --older-than=30d
    ```

---

## Global Options

All subcommands support the following global options:

| Option | Description | Default |
|--------|-------------|---------|
| `--data-dir` | Override default data directory | `~/.local/state/sai/` |
| `--help, -h` | Show help for the subcommand | - |
| `--debug, -d` | Enable debug logging | `false` |

**Examples:**

```bash
# Show help for a specific subcommand
sai list-sessions --help
sai prune-sessions --help

# Enable debug logging for troubleshooting
sai list-sessions --debug
sai delete-sessions -s test-session --debug
```

---

## Data Directory Override

All session management commands respect the `--data-dir` flag, which allows you to:

- **Work with multiple environments**: Separate development, testing, and production sessions
- **Use custom storage locations**: Network drives, encrypted volumes, or cloud-synced directories
- **Backup and restore**: Easily manage session data across different locations
- **Testing**: Use temporary directories for testing without affecting real sessions

**Example Workflow:**

```bash
# Development sessions in default location
sai list-sessions

# Testing sessions in temporary directory
sai list-sessions --data-dir=/tmp/sai-test

# Production sessions on network storage
sai list-sessions --data-dir=/mnt/shared/sai-sessions

# Backup sessions to external drive
sai export-session -s important-session --data-dir=~/.local/state/sai -o /media/backup/session.json
```

---

## See Also

- [CLI Options](cli-options.md) - Command-line flags and options for the main SAI command
- [Environment Variables](environment.md) - Provider configuration via environment variables
- [Persona Format](persona-format.md) - YAML/JSON schema for custom personas
- [Configuration Guide](../getting-started/configuration.md) - Detailed configuration instructions
