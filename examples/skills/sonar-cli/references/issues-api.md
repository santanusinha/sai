# SonarQube Issues API Reference

Use the Web API to programmatically fetch, filter and paginate issues after an
analysis completes.

---

## Base Endpoint

```
GET /api/issues/search
```

Full API docs: `<sonar-host>/web_api/api/issues`

---

## Key Query Parameters

| Parameter | Type | Description |
|---|---|---|
| `projectKeys` | string | Comma-separated project keys |
| `branch` | string | Branch name (requires Developer Edition+ / SonarCloud) |
| `pullRequest` | string | PR identifier |
| `types` | string | Filter by type: `BUG`, `VULNERABILITY`, `CODE_SMELL`, `SECURITY_HOTSPOT` |
| `severities` | string | `BLOCKER`, `CRITICAL`, `MAJOR`, `MINOR`, `INFO` |
| `statuses` | string | `OPEN`, `CONFIRMED`, `REOPENED`, `RESOLVED`, `CLOSED` |
| `resolved` | boolean | `false` = open issues only; `true` = resolved issues only |
| `rules` | string | Filter by rule keys (e.g. `java:S1135`) |
| `tags` | string | Filter by tag (e.g. `security`, `performance`) |
| `assignees` | string | Filter by assignee login |
| `languages` | string | Filter by language (e.g. `java`, `python`, `js`) |
| `componentKeys` | string | Scope to specific files or directories |
| `ps` | int | Page size (max `500`) |
| `p` | int | Page number (1-based) |
| `s` | string | Sort field: `CREATION_DATE`, `UPDATE_DATE`, `CLOSE_DATE`, `ASSIGNEE`, `SEVERITY`, `STATUS`, `FILE_LINE`, `RULE`, `SEVERITY` |
| `asc` | boolean | Sort ascending (default `true`) |
| `createdAfter` | date | Issues created after this date (`YYYY-MM-DD`) |

---

## Example: All Open Issues on a Branch

```bash
HOST="http://localhost:9000"
PROJECT_KEY="my-project"
BRANCH="feature/my-branch"

curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}&branch=${BRANCH}&resolved=false&ps=100&s=SEVERITY&asc=false" \
  | python3 -m json.tool
```

---

## Example: Bugs and Vulnerabilities Only

```bash
curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}&branch=${BRANCH}&types=BUG,VULNERABILITY&resolved=false&ps=100"
```

---

## Response Structure

```json
{
  "total": 35,
  "p": 1,
  "ps": 100,
  "paging": { "pageIndex": 1, "pageSize": 100, "total": 35 },
  "issues": [
    {
      "key": "AXr9...",
      "rule": "java:S2189",
      "severity": "BLOCKER",
      "component": "my-project:src/main/java/Foo.java",
      "project": "my-project",
      "line": 42,
      "textRange": { "startLine": 42, "endLine": 42, "startOffset": 4, "endOffset": 18 },
      "status": "OPEN",
      "message": "Add an end condition to this loop.",
      "effort": "30min",
      "debt": "30min",
      "type": "BUG",
      "tags": [],
      "creationDate": "2024-01-15T10:23:45+0000",
      "updateDate": "2024-01-15T10:23:45+0000"
    }
  ],
  "components": [ ... ],
  "rules": [ ... ],
  "facets": [ ... ]
}
```

### Key Issue Fields

| Field | Description |
|---|---|
| `key` | Unique issue identifier |
| `rule` | Rule key (e.g. `java:S1135`) |
| `severity` | `BLOCKER` / `CRITICAL` / `MAJOR` / `MINOR` / `INFO` |
| `type` | `BUG` / `VULNERABILITY` / `CODE_SMELL` / `SECURITY_HOTSPOT` |
| `component` | `projectKey:path/to/File.java` |
| `line` | Line number in source file |
| `message` | Human-readable issue description |
| `effort` | Estimated remediation effort (e.g. `30min`) |
| `status` | Issue lifecycle status |

---

## Pagination

When `total > ps`, iterate pages:

```bash
PAGE=1
while true; do
  RESPONSE=$(curl -s -u "${SONAR_TOKEN}:" \
    "${HOST}/api/issues/search?projectKeys=${PROJECT_KEY}&branch=${BRANCH}&resolved=false&ps=100&p=${PAGE}")
  echo "$RESPONSE" | python3 -m json.tool
  TOTAL=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['total'])")
  FETCHED=$((PAGE * 100))
  [ "$FETCHED" -ge "$TOTAL" ] && break
  PAGE=$((PAGE + 1))
done
```

---

## Quality Gate Status Endpoint

```
GET /api/qualitygates/project_status
```

```bash
curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/qualitygates/project_status?projectKey=${PROJECT_KEY}&branch=${BRANCH}" \
  | python3 -m json.tool
```

Response:

```json
{
  "projectStatus": {
    "status": "ERROR",
    "conditions": [
      {
        "status": "ERROR",
        "metricKey": "new_coverage",
        "comparator": "LT",
        "errorThreshold": "80",
        "actualValue": "65.4"
      }
    ]
  }
}
```

`status` values: `OK` (gate passed) | `WARN` | `ERROR` (gate failed) | `NONE` (no gate)

---

## Compute Engine Task Status Endpoint

Used to poll for task completion after the scanner runs:

```
GET /api/ce/task?id=<taskId>
```

```bash
curl -s -u "${SONAR_TOKEN}:" \
  "${HOST}/api/ce/task?id=${TASK_ID}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['task']['status'])"
```

Statuses: `PENDING` → `IN_PROGRESS` → `SUCCESS` / `FAILED` / `CANCELLED`
