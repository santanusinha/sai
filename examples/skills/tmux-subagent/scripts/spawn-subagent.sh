#!/usr/bin/env bash
#
# spawn-subagent.sh - Spawn a Sai subagent in a new tmux pane
#
# Usage:
#   spawn-subagent.sh --persona <name> [--split-direction <horizontal|vertical>] [--task "task description"] [--working-dir <path>] [--debug]
#

set -euo pipefail

# Default values
PERSONA=""
SPLIT_DIRECTION="horizontal"
TASK=""
WORKING_DIR="$(pwd)"
SAI_JAR="target/sai-1.0-SNAPSHOT.jar"
DEBUG=false
SCRATCH_DIR="/tmp/sai/${SAI_SESSION_ID:-$(uuidgen)}/scratch"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --persona)
      PERSONA="$2"
      shift 2
      ;;
    --split-direction)
      SPLIT_DIRECTION="$2"
      shift 2
      ;;
    --task)
      TASK="$2"
      shift 2
      ;;
    --working-dir)
      WORKING_DIR="$2"
      shift 2
      ;;
    --debug)
      DEBUG=true
      shift
      ;;
    --help)
      cat <<EOF
Usage: spawn-subagent.sh --persona <name> [OPTIONS]

Spawn a Sai subagent in a new tmux pane with file-based communication.

Required:
  --persona <name>              Persona name (from examples/personas/) or path to persona file

Optional:
  --split-direction <dir>       Split direction: horizontal or vertical (default: horizontal)
  --task <description>          Initial task to send to subagent
  --working-dir <path>          Working directory for subagent (default: current directory)
  --debug                       Pass --debug to subagent for verbose output
  --help                        Show this help message

Communication Protocol:
  - Creates input prompt file in scratch directory
  - Subagent writes output to response file
  - Subagent writes completion marker when done
  - Parent agent polls for completion and reads output

Examples:
  # Spawn a coder subagent in horizontal split
  spawn-subagent.sh --persona coder --split-direction horizontal

  # Spawn a reviewer with an initial task
  spawn-subagent.sh --persona reviewer --task "Review the authentication module"

  # Spawn planner with debug output
  spawn-subagent.sh --persona planner --task "Create a project plan" --debug
EOF
      exit 0
      ;;
    *)
      echo -e "${RED}Error: Unknown option $1${NC}" >&2
      exit 1
      ;;
  esac
done

# Validation
if [[ -z "$PERSONA" ]]; then
  echo -e "${RED}Error: --persona is required${NC}" >&2
  echo "Use --help for usage information" >&2
  exit 1
fi

# Check if running in tmux
if [[ -z "${TMUX:-}" ]]; then
  echo -e "${RED}Error: Not running inside tmux session${NC}" >&2
  echo "Start tmux first: tmux new-session -s mysession" >&2
  exit 1
fi

# Resolve persona path
PERSONA_PATH=""
if [[ -f "$PERSONA" ]]; then
  # Full path provided
  PERSONA_PATH="$PERSONA"
elif [[ -f "examples/personas/${PERSONA}.yaml" ]]; then
  # Name provided, resolve from examples/personas/
  PERSONA_PATH="examples/personas/${PERSONA}.yaml"
elif [[ -f "${HOME}/.config/sai/persona/${PERSONA}.yaml" ]]; then
  # Check user config directory
  PERSONA_PATH="${HOME}/.config/sai/persona/${PERSONA}.yaml"
else
  echo -e "${RED}Error: Persona not found: $PERSONA${NC}" >&2
  echo "Searched in:" >&2
  echo "  - $PERSONA" >&2
  echo "  - examples/personas/${PERSONA}.yaml" >&2
  echo "  - ~/.config/sai/persona/${PERSONA}.yaml" >&2
  exit 1
fi

# Check if Sai JAR exists
if [[ ! -f "$SAI_JAR" ]]; then
  echo -e "${RED}Error: Sai JAR not found at $SAI_JAR${NC}" >&2
  echo "Build it first: mvn clean package" >&2
  exit 1
fi

# Determine split command
SPLIT_CMD=""
case "$SPLIT_DIRECTION" in
  horizontal|h)
    SPLIT_CMD="split-window -h"
    ;;
  vertical|v)
    SPLIT_CMD="split-window -v"
    ;;
  *)
    echo -e "${RED}Error: Invalid split direction: $SPLIT_DIRECTION${NC}" >&2
    echo "Use 'horizontal' or 'vertical'" >&2
    exit 1
    ;;
esac

# Generate unique session ID for subagent
TIMESTAMP=$(date +%s)
SESSION_ID="subagent-${TIMESTAMP}-$$"

# Create scratch directory if it doesn't exist
mkdir -p "$SCRATCH_DIR"

# Setup communication files
INPUT_FILE="${SCRATCH_DIR}/${SESSION_ID}-input.txt"
OUTPUT_FILE="${SCRATCH_DIR}/${SESSION_ID}-output.txt"
MARKER_FILE="${SCRATCH_DIR}/${SESSION_ID}-done.marker"
COMPLETION_MARKER="<<<SUBAGENT_TASK_COMPLETE>>>"

# Create input prompt file if task is provided
if [[ -n "$TASK" ]]; then
  cat > "$INPUT_FILE" <<EOF
$TASK

IMPORTANT: When you have completed this task, write your complete response to the file:
$OUTPUT_FILE

After writing the output file, create a completion marker file:
$MARKER_FILE

And write exactly this text to the marker file:
$COMPLETION_MARKER

This is how I will know you have finished the task.
EOF
  
  echo -e "${BLUE}Created input file: $INPUT_FILE${NC}"
fi

# Build Sai command
SAI_CMD="cd '$WORKING_DIR' && java -jar '$SAI_JAR' --persona '$PERSONA_PATH'"

# Add debug flag if requested
if [[ "$DEBUG" == "true" ]]; then
  SAI_CMD="$SAI_CMD --debug"
fi

# Add input file if task provided
if [[ -n "$TASK" ]]; then
  SAI_CMD="$SAI_CMD --input '$INPUT_FILE'"
fi

# Add exit command at the end if task is provided
if [[ -n "$TASK" ]]; then
  SAI_CMD="$SAI_CMD; exit"
fi

echo -e "${GREEN}Spawning subagent...${NC}"
echo "  Persona: $PERSONA_PATH"
echo "  Split: $SPLIT_DIRECTION"
echo "  Working dir: $WORKING_DIR"
if [[ -n "$TASK" ]]; then
  echo "  Task: $TASK"
  echo "  Communication files:"
  echo "    Input:  $INPUT_FILE"
  echo "    Output: $OUTPUT_FILE"
  echo "    Marker: $MARKER_FILE"
fi
if [[ "$DEBUG" == "true" ]]; then
  echo "  Debug: enabled"
fi
echo ""

# Execute tmux split and send command
tmux $SPLIT_CMD
sleep 0.2  # Brief delay to ensure pane is created

# Send the command to the new pane
tmux send-keys "$SAI_CMD" C-m

echo -e "${GREEN}Subagent spawned successfully!${NC}"

if [[ -n "$TASK" ]]; then
  echo -e "${YELLOW}Waiting for subagent to complete task...${NC}"
  echo -e "${BLUE}Polling for completion marker: $MARKER_FILE${NC}"
  
  # Poll for completion marker
  MAX_WAIT=300  # 5 minutes max
  ELAPSED=0
  while [[ $ELAPSED -lt $MAX_WAIT ]]; do
    if [[ -f "$MARKER_FILE" ]]; then
      MARKER_CONTENT=$(cat "$MARKER_FILE" 2>/dev/null || echo "")
      if [[ "$MARKER_CONTENT" == "$COMPLETION_MARKER" ]]; then
        echo -e "${GREEN}✓ Subagent task completed!${NC}"
        
        # Read and display output
        if [[ -f "$OUTPUT_FILE" ]]; then
          echo -e "\n${GREEN}=== Subagent Output ===${NC}"
          cat "$OUTPUT_FILE"
          echo -e "${GREEN}=== End Output ===${NC}\n"
        else
          echo -e "${YELLOW}Warning: Output file not found: $OUTPUT_FILE${NC}"
        fi
        
        # Cleanup
        rm -f "$INPUT_FILE" "$MARKER_FILE"
        
        break
      fi
    fi
    
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    
    # Show progress every 10 seconds
    if [[ $((ELAPSED % 10)) -eq 0 ]]; then
      echo -e "${BLUE}Still waiting... (${ELAPSED}s elapsed)${NC}"
    fi
  done
  
  if [[ $ELAPSED -ge $MAX_WAIT ]]; then
    echo -e "${YELLOW}Warning: Timeout waiting for subagent completion${NC}"
    echo -e "${YELLOW}Check the tmux pane for status${NC}"
  fi
  
  echo -e "${YELLOW}Pane will close automatically${NC}"
else
  echo -e "${YELLOW}Switch to the new pane: Ctrl+b then arrow keys${NC}"
  echo -e "${YELLOW}Close pane when done: Ctrl+d or type 'exit'${NC}"
fi
