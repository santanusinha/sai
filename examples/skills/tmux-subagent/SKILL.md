---
name: tmux-subagent
description: Spawn specialized Sai subagents (coder, planner, reviewer) in new tmux panes for parallel task execution and delegation
license: Apache-2.0
metadata:
  author: sai-project
  version: "1.2"
compatibility: Requires tmux installed and running inside a tmux session. Requires the `sai` command to be installed (via sai-installer) and available in PATH
---

# Tmux Subagent Spawner



## Purpose
This skill enables you to spawn specialized Sai subagents in new tmux panes. Use this when:
- A task requires specialized expertise (code review, planning, implementation)
- You want to delegate subtasks to run in parallel
- You need persistent, visible subagent sessions the user can interact with
- You want to demonstrate multi-agent coordination patterns
## How It Works
The skill provides a script that:
1. Checks if running inside tmux
2. Creates a new horizontal or vertical split pane
3. Launches a Sai agent with a specified persona in that pane using the `sai` wrapper command
4. **Pane lifetime is tied to the agent process** — when sai exits, the pane closes automatically
5. Uses file-based communication protocol for reliable inter-agent messaging
6. Polls for task completion and automatically retrieves results



### Communication Protocol
The script establishes a robust file-based communication channel:
- **Input File**: Contains task instructions and completion protocol
- **Output File**: Subagent writes results here when complete
- **Marker File**: Signals task completion with special marker
- **Polling**: Parent agent monitors for completion automatically
- **Cleanup**: Temporary files cleaned up after successful completion

This protocol works seamlessly with `--input` mode and enables asynchronous task execution.

## Prerequisites
- Must be running inside a tmux session
- The `sai` command must be installed and available in PATH (installed via `bash sai-installer install`)
- Persona files must exist (default: `examples/personas/*.yaml` or `~/.config/sai/persona/*.yaml`)

## Instructions

### Step 1: Determine if a Subagent is Needed
Before spawning a subagent, ask yourself:
- Does this task require specialized knowledge (coding, reviewing, planning)?
- Would parallel execution be beneficial?
- Is the task substantial enough to warrant a separate agent?

### Step 2: Choose the Right Persona
Available personas (from `examples/personas/`):
- **coder**: For implementation tasks, writing code, file operations
- **planner**: For breaking down complex tasks, research, planning
- **reviewer**: For code review, security audits, quality checks
- **basic**: General-purpose assistant

### Step 3: Execute the Spawn Script
Use the `spawn-subagent.sh` script via bash tool:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona PERSONA_NAME \
  --split-direction [horizontal|vertical] \
  --task "Task description for subagent"
```

**Parameters**:
- `--persona`: Name of persona file (without .yaml extension) or path to persona file
- `--split-direction`: Direction to split (horizontal or vertical, default: horizontal)
- `--task`: Optional initial task to send to the subagent
- `--working-dir`: Optional working directory for the subagent (default: current directory)
- `--debug`: Enable debug output in the subagent pane for visibility
- `--no-wait`: Skip polling for completion (returns immediately after spawn, for parallel scenarios)

**Example**:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --split-direction vertical \
  --task "Implement a new feature for user authentication"
```
**Example (parallel spawn)**:
```bash
# Spawn multiple subagents in parallel
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --task "Implement feature A" \
  --no-wait

bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --task "Implement feature B" \
  --no-wait

# Later, check for completion by looking at marker files:
# ls /tmp/sai/${SAI_SESSION_ID}/scratch/*-done.marker
```


### Step 4: Monitor and Coordinate
After spawning with `--task`:
- Script automatically waits for completion (polls every 2 seconds, max 5 minutes timeout)
- Output is captured and displayed when subagent finishes
- **Pane closes automatically** when agent exits (task completion or error)
- Communication files are cleaned up

Without `--task` (interactive mode):
- The new pane will be visible alongside your current pane
- The subagent waits for interactive commands
- **Pane closes automatically** when you exit sai (Ctrl+C or type 'exit')
- Switch between panes using tmux navigation (Ctrl+b + arrow keys)



**Debug Mode**:
Use `--debug` flag to see real-time progress in the subagent pane while still using the communication protocol.

### Step 5: Integration Pattern
For complex workflows:
1. Use planner subagent to break down the task
2. Use coder subagent(s) for implementation
3. Use reviewer subagent for code review
4. Collect results and synthesize in the main agent

## Available Reference Files
- `usage-examples.md`: Detailed examples and patterns

## Best Practices
- **Clear Task Descriptions**: Provide specific, actionable tasks to subagents
- **Appropriate Personas**: Match persona to task type
- **Pane Management**: Don't spawn too many panes (3-4 max for readability)
- **Use --debug During Development**: See real-time progress and tool calls
- **File-Based Communication**: Reliable for task delegation with automatic result capture
- **Check Scratch Directory**: Communication files in `/tmp/sai/${SAI_SESSION_ID}/scratch/`
- **Error Handling**: Check script exit codes and output for errors

## Limitations
- Requires tmux to be installed and active
- Maximum wait time of 5 minutes for task completion (configurable in script)
- Communication protocol requires write access to `/tmp/sai/` directory
- Spawning too many agents can overwhelm system resources

## Troubleshooting
- **"Not in tmux"**: Run Sai from within a tmux session first
- **"sai command not found"**: Install sai via `bash sai-installer install` and ensure `~/.local/bin` is in PATH
- **"Persona not found"**: Check persona exists in `examples/personas/` or `~/.config/sai/persona/` or provide full path
- **Pane doesn't spawn**: Check tmux version (`tmux -V`), requires tmux 2.0+
- **Timeout waiting for completion**: Task may need more time, check subagent pane for progress
- **Output file not found**: Check scratch directory permissions and subagent errors in pane
- **Communication files remain**: Normal - output files kept for inspection, only input/marker cleaned up
