# sonar-cli Skill

A [Sai Agent Skill](https://agentskills.io/specification) that runs SonarQube / SonarCloud
static-analysis on the current Git branch and surfaces actionable issues (bugs,
vulnerabilities, code smells, security hotspots).

---

## What It Does

1. **Checks / installs** `sonar-scanner` CLI if missing
2. **Detects** the current Git branch automatically
3. **Configures** the analysis — using an existing `sonar-project.properties` or
   guiding you through creating one
4. **Runs** the scanner (`sonar-scanner`, `mvn sonar:sonar`, or `./gradlew sonarqube`)
5. **Waits** for the async analysis task to finish
6. **Reports** open issues in a Markdown table, grouped by severity, with file/line details
7. **Checks** the Quality Gate pass/fail status (optional)

---

## Prerequisites

| Requirement | Notes |
|---|---|
| `sonar-scanner` CLI | Installed via Homebrew, manual download, or Docker — see [`references/installation.md`](references/installation.md) |
| SonarQube server **or** SonarCloud account | Branch analysis requires Developer Edition+ or SonarCloud |
| `SONAR_TOKEN` env var | User or project token from your Sonar instance |
| Git repository | Branch name is read via `git rev-parse --abbrev-ref HEAD` |

---

## Files

```
sonar-cli/
├── SKILL.md                     ← Agent instructions (loaded when skill is activated)
├── references/
│   ├── installation.md          ← How to install sonar-scanner on any platform
│   ├── configuration.md         ← Full sonar-project.properties property reference
│   └── issues-api.md            ← SonarQube Web API: filtering, pagination, response schema
└── scripts/
    ├── report-issues.sh         ← Fetch & print a Markdown issue summary for a branch
    └── wait-for-task.sh         ← Poll the Compute Engine API until analysis completes
```

---

## Quick Start

### 1. Install the skill

```bash
# Symlink from examples (recommended for development)
mkdir -p ~/.config/sai/skills
ln -s "$(pwd)/examples/skills/sonar-cli" ~/.config/sai/skills/

# Or install the packaged .skill file
cp examples/skills/sonar-cli.skill ~/.config/sai/skills/
```

### 2. Set your token

```bash
export SONAR_TOKEN="sqp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

### 3. Ask Sai to run a scan

```
> run sonar analysis on the current branch
> check for sonar issues in this project
> what does SonarQube find in my code?
```

Sai will detect the branch, run the scanner, wait for results, and display a
formatted issue report.

---

## Using the Scripts Directly

### Report issues on the current branch

```bash
export SONAR_TOKEN="sqp_xxxx"

bash examples/skills/sonar-cli/scripts/report-issues.sh \
  --host http://localhost:9000 \
  --project-key my-project
  # --branch feature/my-branch   ← optional; auto-detected from Git if omitted
  # --max-issues 50               ← default: 20
```

Sample output:

```markdown
# SonarQube Issue Report

**Project:** `my-project`
**Branch:**  `feature/my-branch`
**Server:**  http://localhost:9000

## Summary by Severity

| Severity | Count |
|----------|-------|
| BLOCKER  |     2 |
| CRITICAL |     5 |
| MAJOR    |    18 |
| MINOR    |     7 |
| INFO     |     3 |

## Quality Gate

Status: **ERROR**

## Top 20 Open Issues (by Severity)

| # | Severity | Type | Rule | File | Line | Message |
...
```

### Wait for a task to complete

```bash
export SONAR_TOKEN="sqp_xxxx"

bash examples/skills/sonar-cli/scripts/wait-for-task.sh \
  --host http://localhost:9000 \
  --task-id AXr9abc123def \
  --timeout 300 \
  --interval 5
```

---

## SonarCloud vs SonarQube

| | SonarCloud | SonarQube (self-hosted) |
|---|---|---|
| `sonar.host.url` | `https://sonarcloud.io` | Your server URL |
| `sonar.organization` | **Required** | Not used |
| Branch analysis | Included | Developer Edition+ required |
| Token source | sonarcloud.io → My Account → Security | Administration → Security → Tokens |

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `sonar-scanner: command not found` | See [`references/installation.md`](references/installation.md) |
| `Project not found` | Verify `sonar.projectKey` matches exactly what's in the UI |
| `Insufficient privileges` | Token may lack Browse permission on the project |
| Branch not visible in UI | Branch analysis requires Developer Edition+ or SonarCloud |
| SSL certificate errors | Set `sonar.scanner.trustStorePassword` or use a custom truststore |
| Long analysis times | Add `sonar.exclusions` to skip generated/vendor directories |

---

## References

- [SonarQube CLI Documentation](https://docs.sonarsource.com/sonarqube-cli)
- [SonarCloud Documentation](https://docs.sonarsource.com/sonarcloud)
- [sonar-project.properties reference](references/configuration.md)
- [Issues Web API reference](references/issues-api.md)
