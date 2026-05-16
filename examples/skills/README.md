# Sai Agent Skills Examples

This directory contains example Agent Skills for the Sai project. Skills extend the agent's capabilities with specialized knowledge and workflows.

## What are Agent Skills?

Agent Skills follow the [Agent Skills specification](https://agentskills.io/specification). Each skill is a directory containing:
- `SKILL.md`: Required file with YAML frontmatter and instructions
- `scripts/`: Optional executable scripts
- `references/`: Optional documentation and reference files
- `assets/`: Optional templates and resources

## Available Skills

### tmux-subagent
**Purpose**: Spawn specialized Sai subagents in new tmux panes for parallel task execution.

**When to use**:
- Delegate subtasks to specialized personas (coder, planner, reviewer)
- Run multiple agents in parallel
- Create visible, persistent subagent sessions

**Prerequisites**:
- Running inside a tmux session
- Sai JAR built at `target/sai-1.0-SNAPSHOT.jar`

**Example**:
```bash
# From within Sai, activate the skill
> activate_skill("tmux-subagent")

# Spawn a coder subagent
> bash examples/skills/tmux-subagent/scripts/spawn-subagent.sh \
    --persona coder \
    --task "Implement user authentication feature"
```

### sonar-cli
**Purpose**: Run SonarQube / SonarCloud static-analysis on the current Git branch and report issues (bugs, vulnerabilities, code smells).

**When to use**:
- Scan code for quality issues using SonarQube or SonarCloud
- Report code smells, bugs, and security vulnerabilities on the current branch
- Set up `sonar-project.properties` for a project
- Check Quality Gate status after a CI run

**Prerequisites**:
- `sonar-scanner` CLI installed (or Maven/Gradle SonarQube plugin)
- Reachable SonarQube server or SonarCloud account
- `SONAR_TOKEN` environment variable set

**Example**:
```bash
# From within Sai, activate the skill
> activate_skill("sonar-cli")

# Run a scan and report issues on the current branch
export SONAR_TOKEN="sqp_xxxx"
bash examples/skills/sonar-cli/scripts/report-issues.sh \
  --host http://localhost:9000 \
  --project-key my-project
```

## Using Skills

### Method 1: Multi-skill Mode (Default)
Skills are auto-discovered from `~/.config/sai/skills/` by default.

1. Copy or symlink a skill to the skills directory:
   ```bash
   mkdir -p ~/.config/sai/skills
   ln -s /home/santanu/Work/OSS/sai/examples/skills/tmux-subagent ~/.config/sai/skills/
   ```

2. Start Sai normally:
   ```bash
   java -jar target/sai-1.0-SNAPSHOT.jar
   ```

3. List available skills:
   ```
   > list_skills
   ```

4. Activate a skill when needed:
   ```
   > activate_skill("tmux-subagent")
   ```

### Method 2: Single-skill Mode
Load a skill directly at startup:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --skill examples/skills/tmux-subagent
```

In this mode, the skill's instructions are injected directly into context.

### Method 3: Configure in Persona
Add skills to a persona YAML file:

```yaml
agentId: my-agent
name: My Agent
skillDirectories:
  - examples/skills
skillNames:
  - tmux-subagent
```

Then start with the persona:
```bash
java -jar target/sai-1.0-SNAPSHOT.jar --persona my-persona.yaml
```

## Creating Your Own Skills

1. Create a directory with your skill name (lowercase, hyphens only)
2. Create `SKILL.md` with YAML frontmatter:
   ```yaml
   ---
   name: my-skill
   description: What this skill does and when to use it
   ---
   
   # My Skill
   
   ## Instructions
   Step-by-step instructions for the agent...
   ```

3. Add optional `scripts/`, `references/`, `assets/` directories as needed

4. Test the skill:
   ```bash
   java -jar target/sai-1.0-SNAPSHOT.jar --skill path/to/my-skill
   ```

See the [Agent Skills specification](https://agentskills.io/specification) for complete format details.

## Contributing Skills

When adding new example skills:
1. Follow the Agent Skills specification
2. Include comprehensive usage examples in `references/`
3. Document prerequisites and limitations in `SKILL.md`
4. Test in both single-skill and multi-skill modes
5. Update this README with the new skill information
