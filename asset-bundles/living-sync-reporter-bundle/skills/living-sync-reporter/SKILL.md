---
name: living-sync-reporter
description: Use when implementing a Product-Spec-Agent handoff and reporting feature progress, tests, code changes, blockers, notes, or completion to Living Sync.
---

# Living Sync Reporter

Use the bundled Go reporter for all Product-Spec-Agent Living-Sync reporting. Do not hand-write curl calls unless the reporter is missing.

Configuration:
- REST base URL is read from `.claude/living-sync.json`.
- Auth: none.

## Manual Reporting

```bash
sh .claude/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter feature-progress --feature "$FEATURE_ID" --status IN_PROGRESS --summary "Started implementation."
sh .claude/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter test-run --command "npm test" --status passed --summary "Tests passed." --passed 1 --failed 0
sh .claude/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter token-usage --model claude-sonnet --input-tokens 1000 --output-tokens 500
sh .claude/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter code-changes --summary "Implemented auth flow." --file backend/src/main/kotlin/example.kt
sh .claude/skills/global.living-sync-reporter/living-sync-reporter/bin/living-sync-reporter sync-note --severity WARNING --message "Blocked by missing secret."
```

On Windows, use:

```cmd
.claude\skills\global.living-sync-reporter\living-sync-reporter\bin\living-sync-reporter.cmd feature-progress --feature "%FEATURE_ID%" --status IN_PROGRESS --summary "Started implementation."
```

## Hook Behavior

`.claude/settings.json` calls the same reporter on `SessionStart`, `PostToolUse`, and `Stop`.

- `PostToolUse:Bash` reports test/build/check commands as test runs.
- `PostToolUse:Edit|Write|MultiEdit` reports changed files.
- `SessionStart` and `Stop` report lightweight sync notes.

If a hook cannot reach Living Sync, it exits without blocking the agent workflow.
