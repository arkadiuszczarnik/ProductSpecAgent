---
description: Run frontend-reviewer focused on a specific bundle path.
argument-hint: [path]
---

Führe einen Review fokussiert auf `$ARGUMENTS` durch (Default: aktueller Branch gegen `master`).

Delegiere an `frontend-reviewer`:

> Review-Scope: `$ARGUMENTS` (falls leer: kompletter Diff zu `master`).
> - Alle vier Achsen prüfen.
> - Strukturierte Befunde mit file:line, Severity, Fix-Vorschlag.
> - Read-only — keine Änderungen.
