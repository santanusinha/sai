# Installation

## Prerequisites

Before installing SAI, ensure you have:

- **Java 17 or newer** - SAI is built with Java 17 features
- **Maven 3.8+** - Required to build from source
- **Network access** - To connect to your chosen AI model provider

## Verify Java Installation

```bash
java -version
```

You should see output indicating Java 17 or higher:

```
openjdk version "17.0.x" 2023-xx-xx
OpenJDK Runtime Environment (build 17.0.x+x)
OpenJDK 64-Bit Server VM (build 17.0.x+x, mixed mode, sharing)
```

!!! tip "Java Installation"
    If you don't have Java 17+, install it from:
    
    - [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
    - [OpenJDK](https://openjdk.org/)
    - [Amazon Corretto](https://aws.amazon.com/corretto/)
    - [Adoptium/Eclipse Temurin](https://adoptium.net/)

## Build from Source

### Clone the Repository

```bash
git clone https://github.com/santanusinha/sai.git
cd sai
```

### Build with Maven

```bash
mvn clean package
```

This creates a shaded JAR with all dependencies at:

```
target/sai-1.0-SNAPSHOT.jar
```

The shaded JAR is self-contained and can be copied anywhere.

### Create an Alias (Optional)

For convenience, create a shell alias:

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

After adding the alias, reload your shell:

```bash
source ~/.bashrc  # or ~/.zshrc, or restart terminal
```

Now you can run SAI simply with:

```bash
sai
```

## Download Pre-built JAR

!!! warning "Coming Soon"
    Pre-built JARs will be available in GitHub Releases soon. For now, please build from source.

## Verify Installation

Run SAI to verify it's working:

```bash
java -jar target/sai-1.0-SNAPSHOT.jar --version
```

You should see version information printed.

## Next Steps

Now that SAI is installed:

1. [Configure your model provider](configuration.md) - Set up OpenAI, Azure, or Copilot access
2. [Quick Start](quickstart.md) - Run your first session
3. [Running SAI](../guides/running.md) - Learn about different modes

## Troubleshooting

### Maven Build Fails

**Problem:** Build errors related to dependencies

**Solution:** Ensure you're using Maven 3.8+ and have internet access:

```bash
mvn -version
mvn clean package -U  # Force update dependencies
```

### Java Version Mismatch

**Problem:** `UnsupportedClassVersionError` or similar

**Solution:** Verify your JAVA_HOME points to Java 17+:

```bash
echo $JAVA_HOME
$JAVA_HOME/bin/java -version
```

Update JAVA_HOME if needed:

```bash
export JAVA_HOME=/path/to/jdk-17
```

### Build Succeeds but JAR Not Found

**Problem:** `target/sai-1.0-SNAPSHOT.jar` doesn't exist

**Solution:** Check build output for errors:

```bash
mvn clean package | grep -i error
```

The shaded JAR is only created if the build completes successfully.
