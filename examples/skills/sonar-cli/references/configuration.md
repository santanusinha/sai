# sonar-project.properties Configuration Reference

Full reference for the `sonar-project.properties` file and equivalent
command-line / environment-variable overrides.

---

## Minimal Template

```properties
# Required
sonar.projectKey=my-project
sonar.projectName=My Project
sonar.projectVersion=1.0

# Server
sonar.host.url=http://localhost:9000
# Token — better via env var SONAR_TOKEN
# sonar.token=sqp_...

# Sources
sonar.sources=src
sonar.sourceEncoding=UTF-8

# Branch (override at runtime with -Dsonar.branch.name=...)
# sonar.branch.name=main
```

---

## Full Property Reference

### Identity

| Property | Default | Description |
|---|---|---|
| `sonar.projectKey` | _(required)_ | Unique project key; no spaces; safe chars: `a-z A-Z 0-9 - _ . :` |
| `sonar.projectName` | project key | Display name in the UI |
| `sonar.projectVersion` | — | Arbitrary version string shown in the UI |
| `sonar.projectDescription` | — | Free-text description |

### Connection

| Property | Default | Description |
|---|---|---|
| `sonar.host.url` | `http://localhost:9000` | SonarQube server URL; use `https://sonarcloud.io` for SonarCloud |
| `sonar.token` | — | User or project token (prefer env var `SONAR_TOKEN`) |
| `sonar.organization` | — | **SonarCloud only** — organisation key |
| `sonar.scanner.proxyHost` | — | HTTP proxy hostname |
| `sonar.scanner.proxyPort` | — | HTTP proxy port |

### Sources and Tests

| Property | Default | Description |
|---|---|---|
| `sonar.sources` | `.` | Comma-separated list of source directories |
| `sonar.tests` | — | Comma-separated list of test directories |
| `sonar.inclusions` | — | Glob patterns to include (e.g. `**/*.java`) |
| `sonar.exclusions` | — | Glob patterns to exclude (e.g. `**/generated/**`) |
| `sonar.test.inclusions` | — | Glob patterns for test files |
| `sonar.test.exclusions` | — | Glob patterns to exclude from test dirs |
| `sonar.sourceEncoding` | platform default | File encoding (e.g. `UTF-8`) |

### Branch / PR Analysis

| Property | Default | Description |
|---|---|---|
| `sonar.branch.name` | — | Branch name (Developer Edition+ / SonarCloud) |
| `sonar.pullrequest.key` | — | PR identifier (e.g. PR number) |
| `sonar.pullrequest.branch` | — | Source branch of the PR |
| `sonar.pullrequest.base` | — | Target branch of the PR |
| `sonar.pullrequest.github.repository` | — | GitHub `owner/repo` for decorations |

### Coverage and Test Reports

| Property | Default | Description |
|---|---|---|
| `sonar.coverage.jacoco.xmlReportPaths` | — | Path(s) to JaCoCo XML coverage report |
| `sonar.python.coverage.reportPaths` | — | Path(s) to Python coverage XML |
| `sonar.javascript.lcov.reportPaths` | — | LCOV coverage file paths |
| `sonar.testExecutionReportPaths` | — | Generic test-execution XML report |

### Analysis Behaviour

| Property | Default | Description |
|---|---|---|
| `sonar.scm.provider` | auto-detect | Force SCM provider (e.g. `git`) |
| `sonar.scm.disabled` | `false` | Disable SCM blame data |
| `sonar.working.directory` | `.scannerwork` | Temp directory used during analysis |
| `sonar.verbose` | `false` | Enable verbose logging |
| `sonar.scanner.dumpToFile` | — | Dump scanner properties to file for debugging |

---

## SonarCloud vs SonarQube Differences

| Aspect | SonarQube (self-hosted) | SonarCloud |
|---|---|---|
| `sonar.host.url` | Your server URL | `https://sonarcloud.io` |
| `sonar.organization` | Not used | **Required** |
| Token type | User or Project Analysis token | User token from sonarcloud.io |
| Branch analysis | Developer Edition+ required | Included in all plans |

---

## Environment Variables

Any property can be passed as an env var by converting dots to underscores and
prefixing with `SONAR_`:

```bash
export SONAR_TOKEN="sqp_xxxx"
export SONAR_HOST_URL="http://localhost:9000"
export SONAR_PROJECT_KEY="my-project"
```

Or pass as `-D` flags to the scanner:

```bash
sonar-scanner \
  -Dsonar.token="${SONAR_TOKEN}" \
  -Dsonar.host.url="http://localhost:9000" \
  -Dsonar.projectKey="my-project" \
  -Dsonar.branch.name="$(git rev-parse --abbrev-ref HEAD)"
```

---

## Excluding Generated / Vendor Code

```properties
sonar.exclusions=**/generated/**,**/vendor/**,**/*.pb.go,**/node_modules/**,**/target/**
sonar.coverage.exclusions=**/generated/**,**/test/**
```
