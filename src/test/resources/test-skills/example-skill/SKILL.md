---
name: example-skill
description: An example skill demonstrating the SKILL.md format and structure
license: Apache-2.0
metadata:
  author: sai-team
  version: "1.0"
---

# Example Skill

This is an example skill that demonstrates the Agent Skills format.

## When to Use This Skill

Use this skill when you need to understand how Agent Skills work in SAI.

## Instructions

1. Read the skill metadata from the frontmatter
2. Follow the instructions in this Markdown body
3. Use any tools or resources provided by the skill

## Example Usage

```bash
# Activate the skill
activate_skill("example-skill")

# Follow the instructions above
```

## Notes

- Skills are discovered at startup (metadata only)
- Skills are loaded on-demand when activated
- Skills can include references, scripts, and assets
