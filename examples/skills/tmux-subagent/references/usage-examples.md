# Tmux Subagent Usage Examples

This document provides practical examples of using the `tmux-subagent` skill to spawn and manage Sai subagents in separate tmux panes.

## Communication Protocol

The spawn script uses a file-based communication protocol for robust inter-agent communication:

1. **Input File**: Parent agent writes task instructions to `${SCRATCH_DIR}/${SESSION_ID}-input.txt`
2. **Output File**: Subagent writes results to `${SCRATCH_DIR}/${SESSION_ID}-output.txt`
3. **Marker File**: Subagent creates `${SCRATCH_DIR}/${SESSION_ID}-done.marker` with completion marker
4. **Polling**: Parent agent polls for marker file to detect completion
5. **Cleanup**: Input and marker files are automatically cleaned up after task completion

### Benefits of File-Based Communication

- **Asynchronous**: Parent doesn't block waiting for subagent
- **Reliable**: File system ensures message delivery
- **Debuggable**: Can inspect files to troubleshoot
- **Works with --input mode**: No console output needed from subagent
- **Persistent**: Output files remain for inspection

## Example 1: Basic Code Review Task

Spawn a reviewer subagent to analyze a specific file:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona reviewer \
  --task "Review src/main/java/Agent.java for code quality issues" \
  --split-direction horizontal
```

**What happens:**
1. Script creates input file with task + completion instructions
2. Spawns subagent in horizontal split
3. Subagent reads task from input file
4. Subagent writes review to output file
5. Subagent creates marker file with completion marker
6. Script polls for marker, detects completion
7. Script displays output and cleans up
8. Pane closes automatically

## Example 2: Parallel Code Generation with Debug Output

Spawn a coder to generate a utility function, with debug output visible in the pane:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --task "Create a Java utility class for file operations with read, write, and delete methods" \
  --split-direction vertical \
  --debug
```

**Debug mode benefits:**
- See real-time progress in the tmux pane
- Observe tool calls and reasoning
- Useful for debugging subagent behavior
- Output still captured via communication protocol

## Example 3: Project Planning in Custom Directory

Spawn a planner subagent in a specific project directory:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona planner \
  --task "Create a detailed implementation plan for adding authentication to the API" \
  --working-dir /path/to/api-project \
  --split-direction horizontal
```

## Example 4: Interactive Subagent Session

Spawn a subagent without a predefined task for interactive use:

```bash
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --split-direction vertical
```

**Interactive mode:**
- No communication files created
- Pane remains open for manual interaction
- Navigate with `Ctrl+b` then arrow keys
- Close manually with `Ctrl+d` or type `exit`

## Example 5: Multi-Stage Workflow

Use multiple subagents for a complex workflow:

```bash
# Stage 1: Planner creates implementation plan
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona planner \
  --task "Create implementation plan for REST API" \
  --split-direction horizontal

# Stage 2: Coder implements based on plan
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona coder \
  --task "Implement the REST API endpoints as specified in plan.md" \
  --split-direction vertical

# Stage 3: Reviewer checks the implementation
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
  --persona reviewer \
  --task "Review the REST API implementation for security and best practices" \
  --split-direction horizontal
```

## Tmux Navigation Tips

### Switch Between Panes
- `Ctrl+b` then `→` (right arrow): Move to right pane
- `Ctrl+b` then `←` (left arrow): Move to left pane
- `Ctrl+b` then `↑` (up arrow): Move to upper pane
- `Ctrl+b` then `↓` (down arrow): Move to lower pane

### Resize Panes
- `Ctrl+b` then `Ctrl+→`: Expand pane to the right
- `Ctrl+b` then `Ctrl+←`: Expand pane to the left
- `Ctrl+b` then `Ctrl+↑`: Expand pane upward
- `Ctrl+b` then `Ctrl+↓`: Expand pane downward

### Pane Management
- `Ctrl+b` then `z`: Toggle pane zoom (full screen)
- `Ctrl+b` then `x`: Close current pane (confirm with `y`)
- `Ctrl+b` then `{`: Swap with previous pane
- `Ctrl+b` then `}`: Swap with next pane
- `Ctrl+b` then `q`: Show pane numbers

## Communication Protocol Details

### Scratch Directory Location

The script uses `$SAI_SESSION_ID` to determine the scratch directory:
```
/tmp/sai/${SAI_SESSION_ID}/scratch/
```

### File Naming Convention

All communication files use a consistent naming pattern:
```
subagent-<timestamp>-<pid>-<type>.txt
```

Examples:
- `subagent-1772928780-1217579-input.txt`
- `subagent-1772928780-1217579-output.txt`
- `subagent-1772928780-1217579-done.marker`

### Completion Marker

The exact completion marker string is:
```
<<<SUBAGENT_TASK_COMPLETE>>>
```

Parent agent polls for this marker to detect task completion.

### Timeout and Polling

- **Max wait time**: 300 seconds (5 minutes)
- **Poll interval**: 2 seconds
- **Progress updates**: Every 10 seconds
- If timeout occurs, pane remains open for manual inspection

## Troubleshooting

### Problem: "Not running inside tmux session"
**Solution:** Start tmux first:
```bash
tmux new-session -s mywork
```

### Problem: "Persona not found"
**Solution:** Check persona exists:
```bash
ls examples/personas/
# or
ls ~/.config/sai/persona/
```

### Problem: Subagent doesn't complete task
**Solution:** Check the output in the tmux pane:
1. Switch to the subagent pane: `Ctrl+b` then arrow keys
2. Look for errors or stuck processes
3. Check scratch directory for partial output files
4. Use `--debug` flag to see detailed execution

### Problem: Output file not found after completion
**Solution:** 
1. Verify marker file was created correctly
2. Check subagent actually wrote to the output file
3. Inspect the subagent pane for errors
4. Check file permissions on scratch directory

### Problem: Timeout waiting for completion
**Solution:**
1. Task may be complex and need more time
2. Edit script to increase `MAX_WAIT` value
3. Check if subagent is still running in the pane
4. Verify subagent understands the completion protocol

## Best Practices

1. **Use descriptive tasks**: Clearly specify what the subagent should do
2. **Enable debug mode**: Use `--debug` during development to see progress
3. **Choose appropriate personas**: Match persona to task type (coder for code, reviewer for reviews, etc.)
4. **Organize panes**: Use consistent split directions for easier navigation
5. **Monitor progress**: Switch to subagent pane periodically for long-running tasks
6. **Keep output files**: Don't manually delete output files until you've processed them
7. **One task per subagent**: For complex workflows, use multiple sequential subagents
8. **Check scratch directory**: Useful for debugging communication issues

## Advanced Usage

### Custom Scratch Directory

Override the default scratch directory:
```bash
export SAI_SESSION_ID="my-custom-session"
bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh ...
```

### Processing Output Programmatically

Read output file in your parent agent code:
```bash
OUTPUT_FILE="/tmp/sai/${SAI_SESSION_ID}/scratch/subagent-*-output.txt"
if [[ -f $OUTPUT_FILE ]]; then
  cat "$OUTPUT_FILE"
fi
```

### Chain Multiple Subagents

Pass output from one subagent as input to another:
```bash
# First subagent generates plan
bash spawn-subagent.sh --persona planner --task "Create plan" ...

# Read the output
PLAN=$(cat /tmp/sai/.../subagent-*-output.txt)

# Second subagent implements based on plan
bash spawn-subagent.sh --persona coder --task "Implement this plan: $PLAN" ...
```

## Future Enhancements

Potential improvements to the communication protocol:

1. **Status updates**: Subagent writes progress updates to intermediate file
2. **Bidirectional communication**: Parent sends follow-up queries via command file
3. **Structured output**: JSON format for easier parsing
4. **Error reporting**: Separate error file for failures
5. **Resource limits**: Timeout and resource constraints for subagents
