# Installing sonar-scanner CLI

The `sonar-scanner` CLI is the standard way to run SonarQube analysis on any
project, regardless of build tool.

---

## Option 1 — Manual Download (All Platforms)

1. Go to: https://docs.sonarsource.com/sonarqube-cli/latest/
2. Download the latest zip for your OS.
3. Extract and add the `bin/` directory to your `PATH`.

```bash
# Linux / macOS example
SONAR_VERSION="6.2.1.4610"
curl -sSLO "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_VERSION}-linux-x64.zip"
unzip sonar-scanner-cli-${SONAR_VERSION}-linux-x64.zip -d /opt/sonar-scanner
echo 'export PATH="/opt/sonar-scanner/sonar-scanner-${SONAR_VERSION}-linux-x64/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
sonar-scanner --version
```

---

## Option 2 — Homebrew (macOS / Linux)

```bash
brew install sonar-scanner
sonar-scanner --version
```

---

## Option 3 — SDKMAN (JVM-friendly)

SDKMAN does not ship sonar-scanner directly, but you can install it via the
manual method and rely on SDKMAN-managed JVMs.

---

## Option 4 — Docker (No install required)

```bash
docker run --rm \
  -e SONAR_TOKEN="${SONAR_TOKEN}" \
  -v "$(pwd):/usr/src" \
  sonarsource/sonar-scanner-cli \
  -Dsonar.host.url="${SONAR_HOST_URL}" \
  -Dsonar.projectKey="${SONAR_PROJECT_KEY}" \
  -Dsonar.branch.name="${CURRENT_BRANCH}"
```

---

## Option 5 — npm / Node.js wrapper

```bash
npm install -g sonarqube-scanner
# Then run:
sonar-scanner
```

---

## Verify Installation

```bash
sonar-scanner --version
# Expected output like:
# INFO: SonarScanner CLI 6.2.1.4610
# INFO: Java 17.0.x ...
# INFO: OS: Linux ...
```

---

## Java Requirement

sonar-scanner bundles its own JRE from version 5.x onwards. For older versions,
Java 11+ must be available on PATH.
