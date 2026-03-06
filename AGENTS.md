# SAI Agent - Developer Guide

This file is intended for AI coding agents working on the SAI codebase. For full documentation on configuration, CLI usage, and examples, see [README.md](README.md).

## Project Structure

```
.
├── AGENTS.md              # This file (agent-specific guidance)
├── README.md              # Full documentation (config, CLI, examples)
├── pom.xml                # Maven build configuration
├── java-format.xml        # Eclipse formatter rules for Spotless
├── license.header         # Apache 2.0 license header template
├── examples/
│   └── personas/          # Example persona YAML files
└── src/
    ├── main/
    │   ├── java/io/appform/sai/
    │   │   ├── agent/     # Agent instantiation and MCP configuration
    │   │   ├── cli/       # Client-side CLI command handlers (e.g. ! for shell)
    │   │   │   └── handlers/  # CliCommandHandler implementations
    │   │   ├── commands/  # CLI subcommands (list, delete sessions)
    │   │   ├── config/    # Persona/config file loaders
    │   │   ├── models/    # Data models (Session, Actor, Severity)
    │   │   ├── skills/    # Agent Skills extension
    │   │   └── tools/     # Tool implementations (Bash, CoreToolBox)
    │   └── resources/     # Logging config (logback.xml)
    └── test/
        └── java/io/appform/sai/  # Test classes
```

**Key Entry Points:**
- `App.java` → Main entry point, calls `SaiCommand`
- `SaiCommand.java` → Picocli CLI entrypoint
- `SaiAgent.java` → Agent orchestration logic
- `CoreToolBox.java` → File/system tools (read, write, edit, search-replace)
- `BashCommandRunner.java` → Shell command execution

## Key Concepts

- **Provider selection**: `ConfigurableDefaultChatCompletionFactory` reads `MODEL_PROVIDER` env var (`azure`, `openai`, `copilot-proxy`)
- **Tools**: `CoreToolBox` (file ops) and `BashCommandRunner` (shell) are the primary tools available to the agent
- **Session storage**: File-system backed in user's local state directory
- **Personas**: YAML/JSON config files loaded by `AgentConfigLoader`

## Agent Skills

SAI supports the [Agent Skills specification](https://agentskills.io/specification) for extending agent capabilities.

**Key files:**
- `skills/AgentSkillsExtension.java` → Extension implementation
- `skills/SkillRegistry.java` → Skill discovery and loading
- `skills/SkillParser.java` → SKILL.md parser
- `skills/AgentSkill.java`, `skills/SkillMetadata.java` → Data models

**Integration points:**
- `AgentFactory.registerSkillsExtension()` → Register with agent
- `AgentConfig.skillDirectories`, `skillNames` → Configuration
- `SaiCommand.--skill` → Single-skill CLI mode

**Progressive disclosure:**
1. Discovery: Load metadata only (name + description)
2. Activation: Load full SKILL.md via `activate_skill` tool
3. Execution: Load resources via `read_skill_reference` tool

**Modes:**
- **Multi-skill mode**: Skills discovered from directories, activated on-demand via tools
- **Single-skill mode**: One skill loaded directly from `--skill` flag, injected into facts, no tools

## Build and Test

```bash
# Build shaded JAR
mvn -Dspotless.skip=true clean package

# Run tests
mvn -Dspotless.skip=true test

# Format code (required before commits)
mvn spotless:apply

# Check formatting
mvn spotless:check
```

Output JAR: `target/sai-1.0-SNAPSHOT.jar`

## Code Formatting

This project uses Spotless for code formatting. Before committing:

1. Run `mvn spotless:apply` to auto-format
2. Run `mvn compile` to verify (spotless:check runs during compile)

Formatting rules:
- Eclipse formatter with `java-format.xml`
- Apache 2.0 license header from `license.header`
- No wildcard imports
- Specific import ordering: `com, io, org, java, javax, #` (static last)

## Common Paths

| Purpose | Path |
|---------|------|
| Main source | `src/main/java/io/appform/sai/` |
| Resources | `src/main/resources/` |
| Tests | `src/test/java/io/appform/sai/` |
| Build output | `target/` (gitignored) |
| Example personas | `examples/personas/` |

## Documentation Maintenance

When modifying CLI flags, environment variables, or behavior:
1. Update `README.md` (authoritative documentation)
2. If project structure changes, update the tree in this file
3. Run `java -jar target/sai-1.0-SNAPSHOT.jar --help` to verify CLI docs

## Testing Patterns

- **Framework**: JUnit 5 (Jupiter)
- **Naming**: `{ClassName}Test.java` (e.g., `CoreToolBoxLineEditTest.java`)
- **Structure**: Tests use `@BeforeEach`/`@AfterEach` for setup/teardown
- **Temp files**: Use `Files.createTempFile()` and clean up in `@AfterEach`
- **Assertions**: Standard JUnit assertions (`assertEquals`, `assertTrue`, `assertFalse`)
- **No mocking framework** currently in use - tests use real implementations with temp files

## Error Handling

- **Exceptions**: Standard Java exceptions, no custom exception hierarchy
- **Tool responses**: Tools return response objects with `isSuccess()` and `getError()` methods
- **Logging**: SLF4J with Logback
  - Use `log.info()` for tool call receipts
  - Use `log.warn()` for malformed LLM requests
  - Use `log.debug()` for verbose output

## Key Dependencies

| Library | Purpose |
|---------|---------|
| **Picocli** | CLI argument parsing and subcommands |
| **Jackson** | JSON/YAML serialization |
| **Lombok** | Boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`) |
| **Sentinel-AI** | Agent orchestration framework |
| **Failsafe** | Retry and resilience patterns |
| **JLine** | Terminal input handling |

For full configuration details, CLI reference, and examples, see [README.md](README.md).
