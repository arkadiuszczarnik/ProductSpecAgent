Kurzer Einleitungstext für „Product-Spec-Agent“

Product-Spec-Agent ist eine intelligente App für Product Owner, 
die aus einer Idee Schritt für Schritt ein belastbares Produktkonzept macht.
Die Anwendung unterstützt beim Formulieren von Anforderungen, führt strukturiert durch wichtige Produktentscheidungen und hilft dabei, Unklarheiten früh sichtbar zu machen. 
Ziel ist eine Arbeitsweise auf dem Niveau moderner spec-driven Tools wie Superpowers oder Spec Kit: von der ersten Problemdefinition über Scope, 
Priorisierung und Lösungsbild bis hin zu einer umsetzbaren Produktspezifikation. Am Ende entsteht ein sauber strukturiertes, Git-fähiges Repository, das direkt als Grundlage für die weitere Entwicklung mit Claude Code, 
Codex oder anderen AI-Coding-Agents genutzt werden kann. Ergänzt wird das Ganze durch eine attraktive, moderne UI, die komplexe Produktarbeit einfach, schnell und motivierend erlebbar macht.


### Frontend
- **Next.js**
- **React**
- **shadcn/ui**
- **Rete.js**

### Backend / Runtime
- **Kotlin**
**Spring Boot**  für Export, Preview und Integrationen
- **JetBrains Koog**



Für dein Produkt würden sich als Kern-Features besonders anbieten:
1.	Idea-to-Spec Flow: aus grober Idee eine vollständige Produktspezifikation erzeugen.
2.	Guided Decisions: strukturierte Entscheidungshilfen für Scope, Priorisierung, UX, Risiken und MVP.
3.	Clarification Engine: gezielte Rückfragen bei Lücken oder Widersprüchen.
4.	Spec + Plan + Tasks: vom Produktziel direkt zu Umsetzungspaketen.
5.	Git-Repository Output: exportierbare Artefakte für Claude Code / Codex.
6.	Beautiful UI: visuelle, attraktive Oberfläche mit gutem Überblick über Entscheidungen, Specs und nächste Schritte.
7.	Consistency Checks: Prüfung, ob Ziele, Anforderungen, User Stories und Tasks zueinander passen.
8.	Agent-ready Project Handoff: sauber strukturierte Dateien für den Start der Entwicklung mit AI-Coding-Agents.

## AWS Deployment

Die App kann via Pulumi auf AWS EKS deployed werden. Code unter `infra/base/` (VPC, EKS, ECR, S3, IRSA, ALB-Controller) und `infra/workloads/` (Deployments, Services, Ingress).

### Tooling

| Tool | Mindestversion | Install (macOS) |
|---|---|---|
| Pulumi CLI | 3.150+ | `brew install pulumi/tap/pulumi` |
| AWS CLI | 2.x | `brew install awscli` |
| Docker mit `buildx` | 24+ | Docker Desktop |
| kubectl | 1.31+ | `brew install kubectl` |
| Java 21 | — | `brew install openjdk@21` (oder JetBrains Toolchain) |

### Erstmaliges Setup

1. **AWS-Credentials konfigurieren** mit Admin-Rechten (Bootstrap legt IAM-Rollen an):
   ```bash
   aws configure
   ```

2. **Bootstrap** (legt State-Bucket und 4 Pulumi-Stacks an, idempotent):
   ```bash
   ./scripts/pulumi-bootstrap.sh
   ```

3. **OpenAI-API-Key pro Stack setzen:**
   ```bash
   pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-...
   pulumi -C infra/workloads -s prod config set --secret openaiApiKey sk-...
   ```

### Deploy

```bash
./scripts/deploy.sh dev    # oder: prod
```

Skript erzwingt sauberen Working-Tree (Git-SHA wird Image-Tag), führt `pulumi up` für base, baut und pushed beide Images nach ECR und führt `pulumi up` für workloads aus. Erste Apply braucht ca. 20 min (EKS-Cluster-Erstellung), Folge-Deploys ca. 3 min.

### Erwartete Stolperfallen beim ersten Apply

- **ImagePullBackOff:** Beim allerersten Workloads-Apply existiert noch kein Image im ECR (kommt erst durch `deploy.sh`). Pods werden nach dem Push automatisch durch das Kubelet-Rolling repariert. Bei `deploy.sh` ist die Reihenfolge so, dass dies nicht auftritt; nur bei manuellem `pulumi up workloads` ohne vorheriges Push.
- **EKS-Erstellung dauert:** ~15 min für den Cluster, weitere 3 min für ManagedNodeGroup. Geduld.
- **macOS Apple Silicon:** Image-Builds müssen `linux/amd64` cross-bauen (`docker buildx`); Skript macht das bereits.

### Tear-down

```bash
pulumi -C infra/workloads -s dev destroy --yes
pulumi -C infra/base -s dev destroy --yes
```

(State-Bucket bleibt; manuell löschen falls gewünscht.)

### Stack-Layout

- `infra/base/` — Netzwerk, EKS-Cluster, ECR, S3-Bucket, IAM/IRSA, AWS-LBC. Selten geändert.
- `infra/workloads/` — Deployments, Services, Ingress. Wird bei jedem Image-Update angefasst.

State-Backend: S3-Bucket `productspec-pulumi-state-<account-id>` im selben AWS-Account.

Vollständige Spezifikation: `docs/superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md`.