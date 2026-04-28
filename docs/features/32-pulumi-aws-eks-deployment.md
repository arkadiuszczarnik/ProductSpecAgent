# Feature 32: Pulumi-Stack für AWS EKS + S3 Deployment

**Phase:** Infrastruktur / DevOps
**Abhängig von:** Feature 0 (Project Setup), Feature 31 (Project Storage auf S3)
**Aufwand:** L
**Status:** Spec in Arbeit (Brainstorming offen)

## Problem & Ziel

Heute läuft die Anwendung lokal via `./start.sh` oder `docker-compose up`. Es gibt keinen reproduzierbaren Weg, sie auf AWS produktiv zu betreiben. Ohne deklaratives IaC ist jeder Deploy ein manueller Klickpfad in der AWS-Konsole und die Infrastruktur driftet zwischen Umgebungen.

**Ziel:** Ein Pulumi-Stack im Repo (Kotlin/JVM, Pulumi Java SDK), mit dem **dieselbe** Product-Spec-Agent-App, die heute lokal läuft, auf AWS deployt wird:

- **EKS-Cluster** als Container-Plattform für Backend (Spring Boot) und Frontend (Next.js Standalone)
- **S3-Bucket** als persistente Storage-Schicht (ersetzt MinIO aus Feature 31 in Prod)
- **ECR** für die Container-Images
- **IRSA** (IAM Roles for Service Accounts), damit der Backend-Pod ohne Access-Keys auf S3 zugreift
- **Mehrere Stacks** (`dev`, `prod`) aus demselben Code

## Nicht-Ziele

- Multi-Tenant-Cloud-Provisioning für End-User der App (das war Lesart B; hier C)
- Pulumi-Output als Teil des vom Agenten generierten Specs (das war Lesart A)
- Migration vorhandener Production-Daten (es gibt keine Production)
- High-Availability-Setup mit Multi-AZ-Failover-Tests, Disaster Recovery, Backups
- Kosten-Optimierung (Spot Instances, Karpenter, Reserved Instances)

## Tech-Entscheidungen (vorgeschlagen)

| Entscheidung | Empfehlung | Alternative | Wird im Brainstorming geklärt |
|---|---|---|---|
| Pulumi-Sprache | **Kotlin/JVM** (Pulumi Java SDK aus Kotlin) | Java pur | bestätigt durch User |
| Repo-Layout | **`infra/` als eigenes Gradle-Subprojekt** | Pulumi-Code im `backend/`-Modul | offen |
| Container-Registry | **ECR**, im Stack provisioniert | Docker Hub / GHCR | offen |
| K8s-Workloads | **Pulumi `kubernetes.apps.Deployment`** typed in Kotlin | Externe Helm-Charts via Pulumi `helm.Release` | offen |
| Ingress | **AWS Load Balancer Controller + ALB Ingress** | NGINX Ingress + ELB | offen |
| Domain / TLS | **Route53 + ACM** (User stellt Hosted Zone) | Kein Domain-Setup, nur LB-DNS | offen |
| Secrets (OPENAI_API_KEY) | **Pulumi Config `--secret` → K8s Secret** | AWS Secrets Manager + External Secrets | offen |
| Pulumi State-Backend | **S3-Backend** im selben AWS-Account | Pulumi Cloud SaaS | offen |
| Stacks | **`dev` + `prod`** | nur `prod` | offen |
| Image-Build/Push | **`scripts/deploy.sh`** (Docker buildx + ECR push) ausserhalb des Pulumi-Stacks | Pulumi `command:local:Command` | offen |

## Architektur (Skizze)

```
┌──────────────────────────── AWS Account ──────────────────────────┐
│                                                                    │
│  ┌─ VPC (10.0.0.0/16, awsx.ec2.Vpc) ────────────────────────────┐ │
│  │  Public Subnets (3 AZ) ──── ALB (productspec-ingress)         │ │
│  │       │                          │                             │ │
│  │       │                          ▼                             │ │
│  │  Private Subnets (3 AZ) ─── EKS Cluster (eks.Cluster)         │ │
│  │       │                     ManagedNodeGroup (t3.medium x 2)  │ │
│  │       │                          │                             │ │
│  │       │                  ┌───────┴──────────┐                  │ │
│  │       │                  │ backend pod      │                  │ │
│  │       │                  │  SA: backend-sa  │ ── IRSA ─►       │ │
│  │       │                  │ frontend pod     │                  │ │
│  │       │                  └──────────────────┘                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                              │                      │
│  ECR ◄─── docker push ──── deploy.sh        ▼                       │
│                                       S3 Bucket: productspec-{stack}│
│                                       (versioning, AES256)          │
│                                                                     │
│  Route53 Hosted Zone ──► ALB DNS    ACM Cert ──► ALB                │
└─────────────────────────────────────────────────────────────────────┘
```

## Repo-Layout (Vorschlag)

```
ProductSpecAgent/
├── infra/                          # NEU
│   ├── Pulumi.yaml                 # runtime: java, options.binary
│   ├── Pulumi.dev.yaml             # Stack-Config dev
│   ├── Pulumi.prod.yaml            # Stack-Config prod
│   ├── build.gradle.kts            # Pulumi Java SDK + AWS/EKS/K8s
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/agentwork/infra/
│       ├── App.kt                  # Pulumi.run { stack(it) }
│       ├── Networking.kt           # VPC, Subnets
│       ├── Cluster.kt              # EKS, NodeGroup, IAM
│       ├── Storage.kt              # S3 Bucket, IRSA-Role
│       ├── Registry.kt             # ECR Repos (backend, frontend)
│       ├── Workloads.kt            # K8s Deployments, Services, Ingress
│       └── Dns.kt                  # Route53, ACM (optional)
├── scripts/
│   └── deploy.sh                   # NEU: build → ECR push → pulumi up
├── backend/                        # unverändert (außer evtl. application-prod.yml)
└── frontend/                       # unverändert
```

`infra/` wird **nicht** ins root `settings.gradle.kts` aufgenommen — komplett unabhängiges Gradle-Projekt, damit `./gradlew test` im Backend nicht plötzlich Pulumi-Dependencies zieht.

## Pulumi-Komponenten (Skizze)

### `Networking.kt`
- `awsx.ec2.Vpc("productspec-vpc")` mit `cidrBlock=10.0.0.0/16`, public+private Subnets über 3 AZs

### `Cluster.kt`
- `eks.Cluster("productspec-eks")` mit `skipDefaultNodeGroup=true`, `authenticationMode=API`, OIDC-Provider aktiviert
- `iam.Role("productspec-node-role")` + 3 RolePolicyAttachments (WorkerNode, CNI, ECR-ReadOnly)
- `eks.ManagedNodeGroup` mit `instanceTypes=["t3.medium"]`, `scalingConfig: min=1, desired=2, max=4` (dev) / `t3.large`, `min=2, desired=2, max=6` (prod)
- Output: `kubeconfig` (encrypted), `oidcProviderArn`, `oidcProviderUrl`

### `Storage.kt`
- `aws.s3.Bucket("productspec-data-{stack}")` mit:
  - `BucketServerSideEncryptionConfigurationV2` AES256
  - `BucketVersioningV2` enabled
  - `BucketPublicAccessBlock` (alle 4 Toggles `true`)
- `aws.iam.Role("productspec-backend-irsa")` mit OIDC-Trust-Policy für SA `backend-sa` im Namespace `productspec`
- Inline-Policy: `s3:GetObject/PutObject/DeleteObject/ListBucket` auf den Bucket

### `Registry.kt`
- `aws.ecr.Repository("productspec-backend")` und `productspec-frontend`
- `LifecyclePolicy`: untagged > 7 Tage löschen, max 10 tagged
- `imageScanningConfiguration.scanOnPush = true`

### `Workloads.kt`
- `kubernetes.Provider("k8s", kubeconfig=cluster.kubeconfig)`
- Namespace `productspec`
- `ServiceAccount("backend-sa")` mit Annotation `eks.amazonaws.com/role-arn = irsaRole.arn`
- `Deployment("backend")` mit Image aus ECR-Output, env: `S3_BUCKET`, `S3_REGION`, `OPENAI_API_KEY` (von Secret)
- `Deployment("frontend")` mit Image aus ECR, env: `NEXT_PUBLIC_API_URL = https://{domain}/api`
- `Service` ClusterIP für beide
- `Ingress` (ALB) routet `/api/*` → backend, `/*` → frontend
- `Secret("openai")` befüllt aus Pulumi Config `--secret`
- AWS Load Balancer Controller via `helm.Release` (kube-system)

### `App.kt`
```kotlin
import com.pulumi.Pulumi

fun main() {
    Pulumi.run { ctx ->
        val net = networking(ctx)
        val cluster = cluster(ctx, net)
        val storage = storage(ctx, cluster)
        val registry = registry(ctx)
        workloads(ctx, cluster, storage, registry)
        ctx.export("kubeconfig", cluster.cluster.kubeconfig())
        ctx.export("bucketName", storage.bucket.bucket())
        ctx.export("backendImageRepo", registry.backend.repositoryUrl())
    }
}
```

## Konfiguration (Stack-Config-Beispiel)

`Pulumi.dev.yaml`:
```yaml
config:
  aws:region: eu-central-1
  productspec:nodeInstanceType: t3.medium
  productspec:nodeMinSize: 1
  productspec:nodeDesiredSize: 2
  productspec:nodeMaxSize: 4
  productspec:domain: ""              # leer → kein Route53/ACM, nur ALB-DNS
  productspec:openaiApiKey:
    secure: AAABA...                  # via `pulumi config set --secret`
  productspec:imageTag: latest
```

## Deploy-Workflow

```
scripts/deploy.sh prod
  ├── docker buildx build --platform linux/amd64 -t backend:tag backend/
  ├── docker buildx build --platform linux/amd64 -t frontend:tag frontend/
  ├── aws ecr get-login-password | docker login ...
  ├── docker push <ecr-url>/productspec-backend:tag
  ├── docker push <ecr-url>/productspec-frontend:tag
  └── cd infra && pulumi -s prod up --yes -c productspec:imageTag=tag
```

Erste Iteration `pulumi up`: ECR + EKS müssen vor dem ersten Image-Push existieren → der Stack lebt mit einem **Initial-Apply ohne Workloads** (oder mit Platzhalter-Image), dann Push, dann `pulumi up` mit echten Images. Wird im Brainstorming geschärft (z. B. zwei Stacks `infra-base` + `workloads`, oder `dependsOn`-Tricks).

## Akzeptanzkriterien

1. `cd infra && pulumi -s dev up` erstellt aus leerem AWS-Account: VPC, EKS, ECR, S3, IRSA, K8s-Namespace, Workloads, Ingress
2. `kubectl --kubeconfig <(pulumi -s dev stack output kubeconfig --show-secrets)` listet 2 laufende Pods (backend, frontend)
3. ALB-DNS antwortet HTTP 200 auf `/` (Frontend) und `/api/v1/projects` (Backend, nach Login)
4. Backend-Pod liest/schreibt S3 ohne `S3_ACCESS_KEY`/`S3_SECRET_KEY`-Env-Vars (rein über IRSA)
5. `pulumi -s dev destroy` räumt alle Resources rückstandsfrei ab
6. `pulumi -s dev preview` nach `git checkout main` zeigt 0 Changes (idempotenz)
7. README.md hat einen Abschnitt „AWS Deployment" mit Quickstart
8. `infra/`-Subprojekt hat mindestens einen Smoke-Test (z. B. einen `Pulumi.run`-Mock-Test mit `MockResourceMonitor` aus dem Pulumi-Java-Test-Framework), der die Stack-Konstruktion verifiziert

## Out of Scope (YAGNI)

- Karpenter / Cluster Autoscaler (ManagedNodeGroup `min=1` reicht für MVP)
- Multi-Region, Disaster Recovery
- KMS-Customer-Managed-Keys (AES256 SSE-S3 reicht)
- WAF, Shield, GuardDuty
- CloudWatch-Logs/Metrics-Integration (kommt in Folge-Feature)
- CI/CD-Pipeline (GitHub Actions) — `deploy.sh` ist die einzige Eintrittsstelle
- Helm-Chart für die App (alles inline in `Workloads.kt`)
- Migration der MinIO-Daten (lokal-only)

## Offene Fragen (für Brainstorming)

1. **Repo-Layout**: `infra/` Top-Level Gradle-Subprojekt — oder als eigenes Repo? Entscheidung beeinflusst Versionierung der Infra mit dem App-Code.
2. **Image-Tagging-Strategie**: Git-SHA, Semver oder `latest`? `latest` ist einfach aber blockiert Rollbacks.
3. **Ingress vs. NodePort**: Brauchen wir ALB+Domain für MVP, oder reicht der Pod-Service per `kubectl port-forward` für die ersten Iterationen?
4. **Secrets**: Pulumi Config `--secret` (KMS-encrypted im State) → K8s Secret. Reicht das für `OPENAI_API_KEY`, oder brauchen wir AWS Secrets Manager + External Secrets Operator schon jetzt?
5. **Pulumi-Backend**: S3-Backend (kein Pulumi-Cloud-Account nötig) oder Pulumi Cloud (besseres UX)?
6. **Stack-Aufteilung**: Ein Stack pro Umgebung (`dev`, `prod`) oder zusätzlich Trennung Infra-Base (Cluster, ECR, S3) vs. Workloads (Deployments, Ingress) wegen Image-Push-Reihenfolge?
7. **Domain**: Hat der User eine Hosted Zone in Route53 die wiederverwendet wird? Falls nein, MVP ohne Domain.
8. **Migrationspfad MinIO → S3**: Aktuell verwendet die lokale Dev-Umgebung MinIO. Backend-Code muss bereits Endpoint-agnostisch sein (laut Feature 31 ist es das). Bestätigt durch Code-Review.
9. **Region**: `eu-central-1` (Frankfurt, RTL-typisch) als Default?
10. **K8s-Workloads als typed Kotlin oder Helm-Chart**: Typed in Kotlin ist explicit, aber 200+ Zeilen. Helm wäre kompakter, koppelt aber Pulumi an Helm-Tooling.

## Detaildesign

Der Brainstorming-Skill soll auf diesem Skelett aufbauen und einen vollständigen Design-Doc-Entwurf nach `docs/superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md` schreiben.
