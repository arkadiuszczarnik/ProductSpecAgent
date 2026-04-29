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

### Egress / NAT

Worker-Nodes erreichen das Internet (z. B. OpenAI-API) über eine **einzelne NAT-Instance** in `eu-central-1a` (Image `fck-nat-al2023`, `t4g.nano`, EIP attached). Statt drei NAT Gateways (~$114/Monat) ergibt das ~$3/Monat. **Trade-off:** AZ-a-Ausfall = aller Egress fällt aus, bis die Instance wieder läuft. Für Internal-Use okay; für Customer-Facing-Production später auf NAT-pro-AZ upgraden.

Vollständige Spezifikation: `docs/superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md`.

### Kosten-Optimierungen

Aktuell aktiv:
- **ARM-Worker-Nodes** (`t4g.medium`, AWS Graviton) statt x86 — ~30% billiger bei vergleichbarer Java-/Node-Performance
- **Spot-Instances** für Worker-Nodes — 60–90% Rabatt auf EC2-Preise. Bei Termination werden Pods automatisch durch Kubelet-Rolling auf andere Nodes migriert.
- **Single NAT Instance** statt 3 NAT Gateways (siehe Egress / NAT-Abschnitt) — ~$111/Monat Ersparnis
- **EKS-Control-Plane-Logs** mit Retention (Dev 7 Tage, Prod 30 Tage)
- **EBS-Volumes** auf 10 GB pro Node reduziert
- **Image-Build-Platform** `linux/arm64` (passend zu ARM-Workern)

#### Dev-Stack nachts/Wochenenden runterfahren

Spart ~$80/Monat. Manuell pro Schicht-Ende:
```bash
# Worker-Nodes auf 0 skalieren (EKS-Control-Plane bleibt — $73/Monat unvermeidbar)
pulumi -C infra/base -s dev config set productspec-base:nodeDesiredSize "0"
pulumi -C infra/base -s dev up --yes
```

Morgens wieder hoch:
```bash
pulumi -C infra/base -s dev config set productspec-base:nodeDesiredSize "1"
pulumi -C infra/base -s dev up --yes
```

Alternative ohne Pulumi: `aws eks update-nodegroup-config --scaling-config minSize=0,maxSize=2,desiredSize=0 --cluster-name productspec-eks --nodegroup-name productspec-nodes`.

Komplett-Destroy nachts (spart auch die $73 EKS-Fee, aber ~20 min Recreate morgens):
```bash
pulumi -C infra/workloads -s dev destroy --yes
pulumi -C infra/base -s dev destroy --yes
```

#### Prod-Stack erst bei Nutzer-Bedarf

Solange keine echten Nutzer den Prod-Stack brauchen: nicht hochfahren. Spart ~$160/Monat. `./scripts/deploy.sh prod` nur ausführen, wenn Prod tatsächlich gebraucht wird.

#### Geschätzte Monatskosten (eu-central-1)

| Konfiguration | $/Monat |
|---|---|
| Dev (laufend, 1× t4g.medium Spot, Single NAT Instance) | ~$95 |
| Dev (nachts/Weekend Worker auf 0) | ~$50 |
| Dev (komplett destroy nachts/Weekend) | ~$25 |
| Prod (2× t4g.medium Spot, ALB, Single NAT) | ~$160 |