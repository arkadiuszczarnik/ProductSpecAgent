# Feature Set: Product-Spec-Agent

Jedes Feature ist eine eigenständige, implementierbare Einheit. Die Reihenfolge ist so gewählt, dass jedes Feature auf dem vorherigen aufbaut.

## Reihenfolge

| # | Feature | Datei | Abhängig von | Aufwand |
|---|---------|-------|-------------|---------|
| 0 | Project Setup | [00-project-setup.md](00-project-setup.md) | — | L |
| 1 | Idea-to-Spec Flow | [01-idea-to-spec-flow.md](01-idea-to-spec-flow.md) | Feature 0 | XL |
| 2 | Guided Decisions | [02-guided-decisions.md](02-guided-decisions.md) | Feature 0, 1 | L |
| 3 | Clarification Engine | [03-clarification-engine.md](03-clarification-engine.md) | Feature 0, 1 | L |
| 4 | Spec + Plan + Tasks | [04-spec-plan-tasks.md](04-spec-plan-tasks.md) | Feature 0, 1 | XL |
| 5 | Git-Repository Output | [05-git-repository-output.md](05-git-repository-output.md) | Feature 0, 1, 4 | M |
| 6 | Beautiful UI | [06-beautiful-ui.md](06-beautiful-ui.md) | Feature 0 | L |
| 7 | Consistency Checks | [07-consistency-checks.md](07-consistency-checks.md) | Feature 0, 1, 4 | L |
| 8 | Agent-ready Handoff | [08-agent-ready-handoff.md](08-agent-ready-handoff.md) | Feature 0, 4, 5 | L |
| 9 | Spec File Explorer | [09-spec-file-explorer.md](09-spec-file-explorer.md) | Feature 0, 1, 6 | L |
| 10 | Project Scaffold Export | [10-project-scaffold-export.md](10-project-scaffold-export.md) | Feature 4, 5, 8 | M |
| 11 | Guided Wizard Forms | [11-guided-wizard-forms.md](11-guided-wizard-forms.md) | Feature 0, 1, 4, 6 | XL |
| 12 | Dynamische Wizard-Steps | [12-dynamic-wizard-steps.md](12-dynamic-wizard-steps.md) | Feature 11 | M |
| 13 | Wizard-Chat Integration | [13-wizard-chat-integration.md](13-wizard-chat-integration.md) | Feature 11, 1 | L |
| 14 | Agent Locale Detection | [14-agent-locale-detection.md](14-agent-locale-detection.md) | Feature 1 | S |
| 15 | Hide Internal Files in Explorer | [15-hide-internal-files-in-explorer.md](15-hide-internal-files-in-explorer.md) | Feature 9 | S |
| 16 | SPEC-Step entfernen | [16-remove-spec-step.md](16-remove-spec-step.md) | Feature 11, 12, 13 | M |
| 17 | Resizable Sidebar | [17-resizable-sidebar.md](17-resizable-sidebar.md) | Feature 6 | S |
| 18 | Step Blocker Gate | [18-step-blocker-gate.md](18-step-blocker-gate.md) | Feature 2, 3, 11 | M |
| 19 | Explorer Debug Toggle | [19-explorer-debug-toggle.md](19-explorer-debug-toggle.md) | Feature 9, 15 | S |
| 20 | Spec-to-Docs Sync | [20-spec-to-docs-sync.md](20-spec-to-docs-sync.md) | Feature 1, 4, 10, 11 | M |
| 31 | Project Storage auf S3 | [31-project-storage-s3.md](31-project-storage-s3.md) | Feature 0 | L |
| 32 | Pulumi AWS EKS + S3 Deployment | [32-pulumi-aws-eks-deployment.md](32-pulumi-aws-eks-deployment.md) | Feature 0, 31 | L |
| 35 | Feature-Proposal nutzt Upload-Dokumente | [35-feature-proposal-with-uploads.md](35-feature-proposal-with-uploads.md) | Feature 22, 28 | S |

## Architecture Docs

| Thema | Datei |
|-------|-------|
| Monorepo-Struktur | [../architecture/monorepo-structure.md](../architecture/monorepo-structure.md) |
| Authentifizierung | [../architecture/auth.md](../architecture/auth.md) |
| REST API Design | [../architecture/rest-api.md](../architecture/rest-api.md) |
| Persistenz | [../architecture/persistence.md](../architecture/persistence.md) |
| Koog Agents | [../architecture/koog-agents.md](../architecture/koog-agents.md) |

## Frontend Docs

| Thema | Datei |
|-------|-------|
| Design System | [../frontend/design-system.md](../frontend/design-system.md) |

## Tech-Stack

### Frontend
- Next.js (App Router) + React + TypeScript
- shadcn/ui (Component Library)
- Rete.js (Node-Graph Visualisierung)

### Backend
- Kotlin 2.2 + Spring Boot 4
- JetBrains Koog (AI Agent Framework)
- Filesystem/Git Persistenz

### Deployment
- Docker Compose (Self-hosted)
- Multi-User mit JWT Auth
