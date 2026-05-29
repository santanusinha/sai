# Installation

SAI ships with a self-contained installer script (`sai-installer`) that handles everything: dependency checks, building from source, creating the `sai` launcher, and seeding your config directory with bundled personas and skills.

## Quick Install (recommended)

### Prerequisites

The installer will automatically install Java 17 and Maven if they are missing. All you need upfront is:

- **git** — to clone the repository
- **curl** or **wget** — to download the installer
- **sudo** access — only if Java / Maven need to be installed by your package manager

Supported platforms: **Linux** (apt, dnf, pacman, zypper) and **macOS** (Homebrew).

### One-line install

```bash
curl -fsSL https://raw.githubusercontent.com/santanusinha/sai/master/sai-installer | bash -s -- install
```

Or download and inspect it first (recommended):

```bash
curl -fsSL https://raw.githubusercontent.com/santanusinha/sai/master/sai-installer -o sai-installer
bash sai-installer install
```

### What the installer does

1. Detects your OS (Linux / macOS)
2. Verifies Java 17+ is present — installs it automatically if not
3. Verifies Maven 3.8+ is present — installs it automatically if not
4. Clones the SAI repository into `~/.local/share/sai/repo`
5. Builds the shaded JAR with Maven
6. Writes a `sai` launcher script to `~/.local/bin/sai`
7. Creates your config directory at `~/.config/sai/` with:
    - `persona/` — bundled personas copied from `examples/personas/`
    - `skills/`  — bundled skills copied from `examples/skills/`
    - `.env`     — template environment file (fill in your API key)
8. Saves `sai-installer` itself to `~/.local/bin/sai-installer` for later use
9. Adds `~/.local/bin` to your PATH in your shell rc file (with your approval)

### After installation

Reload your shell (or open a new terminal), then verify:

```bash
sai --version
```

Edit `~/.config/sai/.env` to add your model provider credentials:

```bash
nano ~/.config/sai/.env
```

---

## Upgrading

```bash
sai-installer upgrade
```

This pulls the latest commits, rebuilds the JAR, updates the launcher, and refreshes bundled personas and skills.

## Reinstalling

```bash
sai-installer reinstall
```

Removes `~/.local/share/sai` and performs a clean install. **Your config directory (`~/.config/sai`) is preserved.**

## Uninstalling

```bash
sai-installer uninstall
```

Removes the JAR, launcher, and installer. Optionally removes `~/.config/sai` (you will be prompted).

---

## Custom install location

Use `--base-dir` to install SAI under a non-default root (useful for testing or multi-user setups):

```bash
bash sai-installer --base-dir /opt/sai install
```

All paths are derived from `<base-dir>`:

| Path | Purpose |
|---|---|
| `<base-dir>/.local/share/sai/` | Cloned repository and built JAR |
| `<base-dir>/.local/bin/sai` | Launcher script |
| `<base-dir>/.local/bin/sai-installer` | Installer copy |
| `<base-dir>/.config/sai/` | Config, personas, skills, `.env` |
| `<base-dir>/.local/state/sai/` | Install log, session data |

---

## Managing skills and personas after install

The installer also serves as the package manager for skills and personas:

```bash
# Install a skill from a local directory
sai-installer skill-install /path/to/skill-dir

# Install a skill from a GitHub repo
sai-installer skill-install owner/repo
sai-installer skill-install owner/repo/sub/path

# Install a skill from a full git URL (optionally with a sub-path)
sai-installer skill-install https://github.com/owner/repo.git#sub/path

# Install a skill from a zip or tar.gz
sai-installer skill-install https://example.com/skill.zip

# Remove a skill
sai-installer skill-remove <skill-name>

# Install a persona from a local file
sai-installer persona-install /path/to/persona.yaml

# Install a persona from GitHub (owner/repo/path/to/file.yaml)
sai-installer persona-install owner/repo/personas/my-persona.yaml

# Install a persona from a GitHub blob or raw URL
sai-installer persona-install https://github.com/owner/repo/blob/master/personas/p.yaml

# Remove a persona
sai-installer persona-remove <persona-name>
```

---

## Build from source (advanced)

If you prefer to manage the build manually, or want to work on SAI itself, you can skip the installer:

### Prerequisites

- Java 17 or newer
- Maven 3.8+

### Steps

```bash
git clone https://github.com/santanusinha/sai.git
cd sai
mvn clean package
```

This creates a shaded JAR at:

```
target/sai-1.0-SNAPSHOT.jar
```

Run it directly:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar
```

Or create a shell alias for convenience:

=== "Bash/Zsh"

    ```bash
    # Add to ~/.bashrc or ~/.zshrc
    alias sai='java -jar /path/to/sai/target/sai-1.0-SNAPSHOT.jar'
    ```

=== "Fish"

    ```fish
    # Add to ~/.config/fish/config.fish
    alias sai='java -jar /path/to/sai/target/sai-1.0-SNAPSHOT.jar'
    ```

---

## Next Steps

Now that SAI is installed:

1. [Configure your model provider](configuration.md) — Set up OpenAI, Azure, or Copilot access
2. [Quick Start](quickstart.md) — Run your first session
3. [Running SAI](../guides/running.md) — Learn about different modes

---

## Troubleshooting

### Java / Maven not installing automatically

The installer uses your system package manager. If none is detected, install manually:

- Java 17+: [Adoptium/Eclipse Temurin](https://adoptium.net/) (recommended), [OpenJDK](https://openjdk.org/), [Amazon Corretto](https://aws.amazon.com/corretto/)
- Maven 3.8+: [maven.apache.org](https://maven.apache.org/install.html)

### `sai: command not found` after install

Your shell rc file was updated, but you need to reload it:

```bash
source ~/.bashrc   # or ~/.zshrc
# Or open a new terminal
```

If you declined the PATH update during install, add it manually:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

### Maven build fails

```bash
cd ~/.local/share/sai/repo
mvn clean package -U   # force-refresh dependencies
```

### Java version mismatch

```bash
echo $JAVA_HOME
$JAVA_HOME/bin/java -version
export JAVA_HOME=/path/to/jdk-17
```

### Reinstall after a broken state

```bash
sai-installer reinstall
```

This wipes `~/.local/share/sai` and performs a clean build while preserving your config.
