---
name: sonar-cli
description: >
  Run SonarQube / SonarCloud static-analysis on the current Git branch and
  surface actionable issues using the sonar-scanner CLI. Use this skill
  whenever the user asks to: scan code with Sonar, check for code smells or
  bugs via SonarQube, run a Sonar analysis, report Sonar issues on the current
  branch, set up sonar-project.properties, or integrate SonarQube into a
  project workflow. Trigger even if the user just says "run sonar", "sonar
  scan", "check code quality with sonar", "what issues does sonar find", or
  mentions SonarQube/SonarCloud quality gates.
license: Apache-2.0
metadata:
  author: sai-project
  version: "1.0"
compatibility: >
  Requires sonar-scanner CLI. Java projects also work with the SonarQube Maven/Gradle
  plugins. A reachable SonarQube server or SonarCloud account is needed for full
  reporting; local-only scanning (--dry-run style) is not supported by the CLI itself.
---

# SonarQube CLI Skill

Run SonarQube or SonarCloud static-analysis on the current Git branch and present
a clear summary of issues (bugs, vulnerabilities, code smells, security hotspots).

---

## Step 0 — Verify / Install sonar-scanner

Check whether `sonar-scanner` is already on PATH:

```bash
sonar-scanner --version 2>/dev/null || echo "NOT_FOUND"
```

If missing, install it. Read `references/installation.md` for platform-specific
instructions (manual download, Homebrew, SDKMAN, Docker).

---

## Step 1 — Detect the Current Branch

The branch name is used to tell SonarQube which branch is being analysed:

```bash
git rev-parse --abbrev-ref HEAD
```

Save this as `CURRENT_BRANCH`.

---

## Step 2 — Locate or Create Project Configuration

SonarQube needs a project key, server URL, and token. These come from one of:

1. **`sonar-project.properties`** in the repo root (preferred for teams)
2. **Command-line `-D` flags** (convenient for one-off runs)
3. **Environment variables** (ideal for CI)

### Checking for an existing config

```bash
cat sonar-project.properties 2>/dev/null || echo "No sonar-project.properties found"
```

### Minimum required properties

| Property | Description |
|---|---|
| `sonar.projectKey` | Unique identifier in SonarQube/SonarCloud |
| `sonar.host.url` | Server URL (e.g. `https://sonarcloud.io` or `http://localhost:9000`) |
| `sonar.token` | Auth token (prefer env var `SONAR_TOKEN` over plain-text in file) |
| `sonar.sources` | Comma-separated source dirs (default: `.`) |
| `sonar.branch.name` | Git branch being analysed — set this to `CURRENT_BRANCH` |

If no `sonar-project.properties` exists and the user hasn't provided the
required values, ask for:
- SonarQube server URL (or confirm SonarCloud)
- Project key
- Auth token (or environment variable name holding it)

Read `references/configuration.md` for a full property reference and
`sonar-project.properties` template.

---

## Step 3 — Run the Analysis

With a `sonar-project.properties` already configured, run:

```bash
sonar-scanner \
  -Dsonar.branch.name="${CURRENT_BRANCH}"
```

Passing the token via environment variable is safer than the command line:

```bash
export SONAR_TOKEN="<your-token>"
sonar-scanner -Dsonar.branch.name="${CURRENT_BRANCH}"
```

For Maven projects, the equivalent is:

```bash
mvn sonar:sonar \
  -Dsonar.branch.name="${CURRENT_BRANCH}" \
  -Dsonar.token="${SONAR_TOKEN}"
```

For Gradle:

```bash
./gradlew sonarqube \
  -Dsonar.branch.name="${CURRENT_BRANCH}" \
  -Dsonar.token="${SONAR_TOKEN}"
```

> **Note on branch analysis**: Branch analysis requires a Developer Edition
> or higher SonarQube licence, or SonarCloud. Community Edition only supports
> the main branch. If the server doesn't support branches, omit
> `-Dsonar.branch.name` and the analysis runs against the default branch slot.

Capture the scanner output — it contains the **task URL** needed in Step 4.

---

## Step 4 — Wait for the Analysis Task to Complete

The scanner submits analysis results asynchronously. The output includes a
line like:

```
INFO: More about the report processing at http://<host>/api/ce/task?id=<taskId>
```

Poll until the task status is `SUCCESS` (or `FAILED`):

```bash
# Replace <host>, <token>, and <taskId> with actual values
curl -s -u "${SONAR_TOKEN}:" \
  "http://<host>/api/ce/task?id=<taskId>" \
  | python3 -m json.tool | grep '"status"'
```

Typical statuses: `PENDING` → `IN_PROGRESS` → `SUCCESS` / `FAILED`.

A helper script is available at `scripts/wait-for-task.sh`.

---

## Step 5 — Fetch and Report Issues

Once the task is `SUCCESS`, query the Issues API:

```bash
PROJECT_KEY="<your-project-key>"
BRANCH="${CURRENT_BRANCH}"
HOST="<your-sonar-host>"

curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}&branch=${BRANCH}&resolved=false&ps=50" \
  | python3 -m json.tool
```

Read `references/issues-api.md` for filtering parameters, pagination, and
how to interpret the JSON response.

### Presenting the report

Summarise findings in a table grouped by severity:

```
## Sonar Analysis — <project> @ <branch>

| Severity    | Count |
|-------------|-------|
| BLOCKER     |   2   |
| CRITICAL    |   5   |
| MAJOR       |  18   |
| MINOR       |   7   |
| INFO        |   3   |

### Top Issues
...
```

Show the top 10 issues with: severity, rule key, file path, line number, and
the issue message. Provide the SonarQube dashboard URL so the user can drill
in.

The reporting script at `scripts/report-issues.sh` automates Steps 4 + 5 and
prints a Markdown summary.

---

## Step 6 — Quality Gate Status (Optional)

Check whether the project passes the Quality Gate:

```bash
curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/qualitygates/project_status?projectKey=${PROJECT_KEY}&branch=${BRANCH}" \
  | python3 -m json.tool
```

A `status` of `OK` means the gate passed; `ERROR` means it failed.

---

## Reference Files

| File | When to read |
|---|---|
| `references/installation.md` | sonar-scanner is missing; need install instructions |
| `references/configuration.md` | Need to create/review `sonar-project.properties` |
| `references/issues-api.md` | Need to filter, paginate, or interpret issues |

---

## Tips and Troubleshooting

- **"Project not found"**: Verify `sonar.projectKey` matches exactly what's in the UI.
- **"Insufficient privileges"**: The token may lack Browse permission on the project.
- **Branch not visible in UI**: Branch analysis requires a paid edition / SonarCloud.
- **Long analysis times**: Exclude generated or vendor directories via `sonar.exclusions`.
- **SSL errors**: Set `sonar.scanner.trustStorePassword` or point to a custom truststore.
- For SonarCloud, `sonar.host.url` must be `https://sonarcloud.io` and you also need
  `sonar.organization=<org-key>`.
