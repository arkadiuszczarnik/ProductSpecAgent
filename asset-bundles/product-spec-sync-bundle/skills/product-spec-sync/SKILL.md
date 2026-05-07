---
name: product-spec-sync
description: Use before larger implementation or planning work in a Product-Spec-Agent handoff project to refresh the local handoff ZIP, inspect changes, and adjust the plan before coding.
---

# Product Spec Sync

Use this skill when working in a project managed by Product-Spec-Agent and the handoff Markdown contains a Sync-URL.

## How to Sync This Project

Dieses Projekt wird vom Product-Spec-Agent verwaltet. Die hier vorliegende Spec
ist eine **Momentaufnahme**. Wenn du Updates brauchst, hol dir die aktuelle
Version vom Service:

```bash
curl -L -o handoff.zip "<SYNC_URL_FROM_HANDOFF_MARKDOWN>"
unzip -o handoff.zip
rm handoff.zip
```

- **Sync-URL:** steht im generierten Handoff-Markdown.
- **Method:** `GET` (kein Auth, kein Body)
- **Response:** ZIP mit `CLAUDE.md`, `AGENTS.md`, `implementation-order.md`, `SPEC.md`, `decisions/`, `clarifications/`, `tasks/`, `documents/`.

**Empfohlenes Vorgehen vor jeder groesseren Aenderung:**

1. Sync ziehen (`curl ...` wie oben).
2. `git diff` auf den entpackten Files pruefen - gibt es Aenderungen am Spec?
3. Falls ja: Plan anpassen, mit dem User abstimmen, dann erst implementieren.
