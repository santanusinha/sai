---
name: mkdocs-to-zensical
description: >
  Migrate MkDocs projects to the Zensical documentation platform by converting mkdocs.yml
  to a native zensical.toml configuration file. Use this skill whenever the user wants to:
  migrate from MkDocs to Zensical, convert mkdocs.yml to TOML format, set up Zensical for
  a project that previously used MkDocs Material, or update CI/CD workflows after a
  MkDocs-to-Zensical migration. Trigger even if the user just says "convert my docs to
  Zensical", "set up Zensical", "replace mkdocs with zensical", or "zensical migration".
  Also trigger when the user provides a mkdocs.yml file and asks for a Zensical config.
---

# MkDocs → Zensical Migration Skill

Zensical is a documentation platform compatible with MkDocs Material but uses a native
TOML configuration file (`zensical.toml`) instead of `mkdocs.yml`. This skill guides a
complete, safe migration.

## Quick Overview of Changes

| What changes | MkDocs (YAML) | Zensical (TOML) |
|---|---|---|
| Config file | `mkdocs.yml` | `zensical.toml` |
| Config scope | Top-level keys | Everything under `[project]` |
| Nav list | YAML list | TOML inline array-of-tables |
| Palette | YAML list under `theme.palette` | `[[project.theme.palette]]` array of tables |
| Emoji class refs | `!!python/name:material.extensions.emoji.*` | `zensical.extensions.emoji.*` |
| `search` plugin | Explicit in `plugins:` | Built-in, no config needed |
| `minify` plugin | Explicit in `plugins:` | Built-in, omit entirely |
| Other plugins | `plugins: - name:` | `[project.plugins.name]` tables |
| Social links | YAML list under `extra.social` | `[[project.extra.social]]` array of tables |
| `extra_css` | YAML list of CSS file paths | `extra_css = [...]` array under `[project]` |
| `extra_javascript` | YAML list of JS file paths | `extra_javascript = [...]` array under `[project]` |

## Step-by-Step Migration

### 0. Fetch the Zensical documentation

Before starting, ensure the official Zensical docs are available locally at `/tmp/zensical-docs`:

```bash
if [ ! -d /tmp/zensical-docs ]; then
  git clone --depth=1 https://github.com/zensical/docs /tmp/zensical-docs
fi
```

Use these local docs as the authoritative reference throughout the migration —
read them whenever you are uncertain about a config key, syntax, or whether a
feature is supported. Key files:

| Topic | File |
|---|---|
| All `[project]` settings, unsupported keys | `/tmp/zensical-docs/docs/setup/basics.md` |
| Nav syntax | `/tmp/zensical-docs/docs/setup/navigation.md` |
| `extra_css` / `extra_javascript` | `/tmp/zensical-docs/docs/customization.md` |
| Theme palette / colors | `/tmp/zensical-docs/docs/setup/colors.md` |
| Social links (footer) | `/tmp/zensical-docs/docs/setup/footer.md` |
| Python Markdown extensions | `/tmp/zensical-docs/docs/setup/extensions/python-markdown.md` |
| PyMdown extensions | `/tmp/zensical-docs/docs/setup/extensions/python-markdown-extensions.md` |
| Tags plugin status | `/tmp/zensical-docs/docs/setup/tags.md` |

### 1. Read the source files

Identify:
- `mkdocs.yml` (required) — the main config to convert
- Any `.pages` files in the docs tree — used for directory-level nav ordering when `mkdocs.yml` doesn't have a full `nav:` block
- `.github/workflows/*.yml` files that reference `mkdocs.yml` in path triggers or build steps
- Any doc files that mention `mkdocs.yml` (READMEs, deployment guides, etc.)

### 2. Build the TOML structure

All settings live under `[project]`. Follow this order in the output file:

```toml
# ── Scalar project settings ──────────────────────────────────────────────────
[project]
site_name        = "..."
site_description = "..."
site_author      = "..."
site_url         = "..."
repo_name        = "..."
repo_url         = "..."
edit_uri         = "..."
copyright        = "..."    # if present in mkdocs.yml

nav = [...]                 # see Nav Conversion below

# ── Theme ────────────────────────────────────────────────────────────────────
[project.theme]
features = [...]
font.text = "..."
font.code = "..."

[project.theme.icon]
repo = "..."
logo = "..."    # if present

[[project.theme.palette]]   # repeat block for each palette entry
...

# ── Extra ────────────────────────────────────────────────────────────────────
[project.extra]
generator = false
version.provider = "..."    # if present

[[project.extra.social]]    # repeat block for each social link
icon = "..."
link = "..."

# ── Plugins (non-search, non-minify only) ────────────────────────────────────
[project.plugins.plugin-name]
key = value

# ── Extra CSS / JavaScript ───────────────────────────────────────────────────
extra_css         = ["stylesheets/extra.css"]   # if present
extra_javascript  = ["javascripts/extra.js"]    # if present

# ── Markdown extensions ──────────────────────────────────────────────────────
[project.markdown_extensions.ext-name]
# empty table = extension enabled with no options
key = value    # options if the extension had them
```

### 3. Nav Conversion

**When mkdocs.yml has a `nav:` block** — convert directly:

MkDocs YAML nav:
```yaml
nav:
  - Home: index.md
  - Getting Started:
    - Installation: install.md
    - Config: config.md
  - API: api.md
```

Zensical TOML nav (inline array):
```toml
nav = [
  {"Home" = "index.md"},
  {"Getting Started" = [
    {"Installation" = "install.md"},
    {"Config" = "config.md"}
  ]},
  {"API" = "api.md"}
]
```

Key rules:
- Each nav item is a TOML inline table `{key = value}`
- A flat page is `{"Title" = "path.md"}`
- A section is `{"Section" = [list of inline tables]}`
- String keys with spaces must be quoted
- File paths stay as-is (relative to `docs_dir`)

**When there is NO `nav:` block in mkdocs.yml** but `.pages` files exist — synthesize nav from them:

A `.pages` file like:
```yaml
nav:
  - index.md
  - install.md
  - guides:
    - overview.md
```

…produces a nav section for that directory. Walk the docs tree: for each directory that has a `.pages` file, use its `nav:` list. For entries without a title, infer a human-readable title from the filename (strip `.md`, replace `-`/`_` with spaces, title-case). Produce the same TOML inline array format.

### 4. Emoji Class Rewrite

Anywhere you see:
```yaml
emoji_index: !!python/name:material.extensions.emoji.twemoji
emoji_generator: !!python/name:material.extensions.emoji.to_svg
```

Output as:
```toml
[project.markdown_extensions.pymdownx.emoji]
emoji_index     = "zensical.extensions.emoji.twemoji"
emoji_generator = "zensical.extensions.emoji.to_svg"
```

The `!!python/name:` YAML tag is a MkDocs-specific mechanism. In Zensical TOML, these are plain string values with the `zensical.extensions.emoji.*` namespace.

Similarly, `!!python/name:pymdownx.superfences.fence_code_format` becomes the plain string `"pymdownx.superfences.fence_code_format"`.

### 5. Plugin Handling

| MkDocs plugin | Zensical action |
|---|---|
| `search` (with or without options) | **Omit** — built-in, no config needed |
| `minify` | **Omit** — minification is built-in |
| `git-revision-date-localized` | Map to `[project.plugins.git-revision-date-localized]` with same options |
| `tags` | Note: Zensical's tag index support is in progress; add `[project.plugins.tags]` but warn the user |
| Any other plugin | Map to `[project.plugins.<name>]` with its options |

### 6. Unsupported mkdocs.yml Settings

The following keys have **no Zensical equivalent** — silently drop them:
`remote_branch`, `remote_name`, `exclude_docs`, `draft_docs`, `not_in_nav`,
`validation`, `strict`, `hooks`, `watch`

Drop `docs_dir` if it would be set to `'.'` (temporary Zensical limitation).
If `docs_dir` is set to a non-`.` custom path, preserve it.

If `extra_css` is present, map it to a TOML array under `[project]`:

```toml
extra_css         = ["stylesheets/extra.css"]   # if present
extra_javascript  = ["javascripts/extra.js"]    # if present
```

Similarly map `extra_javascript` if present. These are supported natively in Zensical
(see https://zensical.org/docs/customization/?h=css#additional-css).

### 7. Update Downstream References

After writing `zensical.toml`, scan for and update these references:

**GitHub Actions workflows** (`.github/workflows/*.yml`):
- Path triggers: `paths: [mkdocs.yml, ...]` → include `zensical.toml`
- Build steps that run `mkdocs build` or `mkdocs gh-deploy` → change to `zensical build` / `zensical gh-deploy`
- Pip install steps with `mkdocs-material` → change to `zensical`

**Documentation files** (READMEs, deployment guides, etc.):
- Any prose that says "Edit `mkdocs.yml`" → update to "Edit `zensical.toml`"
- Links pointing to `mkdocs.yml` in the repo → update to `zensical.toml`

**Delete `mkdocs.yml`** only after `zensical.toml` has been written and validated.

### 8. Validate the Output

After writing `zensical.toml`, validate it with Python's `tomllib` (Python 3.11+) or `tomli`:

```python
import sys
try:
    import tomllib
except ImportError:
    import tomli as tomllib  # pip install tomli

with open("zensical.toml", "rb") as f:
    data = tomllib.load(f)
print("✓ TOML valid")
```

Run this as a quick sanity check before telling the user the migration is done.

## Common Pitfalls

- **`[[double brackets]]` for arrays**: `theme.palette` and `extra.social` use TOML Array of Tables syntax — each entry needs `[[project.theme.palette]]` or `[[project.extra.social]]`, not `[project.theme.palette]`.
- **Inline table quoting**: In TOML inline tables `{"Key with spaces" = "val"}`, keys containing spaces must be quoted.
- **No trailing commas**: TOML does not allow trailing commas in inline arrays.
- **Empty extension tables**: Extensions with no options still need an empty table declaration, e.g. `[project.markdown_extensions.admonition]` — do not omit them.
- **toc extension**: The `toc` extension with `permalink: true` is fine to include as `[project.markdown_extensions.toc]` with `permalink = true`, but check Zensical docs — some toc options may not be recognized.

## Output Summary

After completing the migration, present a brief summary:
- `zensical.toml` created ✓
- `mkdocs.yml` deleted ✓ (or retained if user asked)
- Plugins omitted (built-in): search, minify
- Plugins mapped to TOML: list any others
- Downstream files updated: list changed files
- Any warnings (e.g., tags plugin partial support, unsupported settings dropped)
