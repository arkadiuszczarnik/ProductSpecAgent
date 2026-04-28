# Design: Pulumi-Stack für AWS EKS + S3 Deployment

**Feature:** [32 — Pulumi AWS EKS + S3 Deployment](../../features/32-pulumi-aws-eks-deployment.md)
**Datum:** 2026-04-28
**Status:** Approved
**Zielgruppe:** Implementer der nachfolgenden Phase (writing-plans → subagent-driven-development)

## Problem & Ziel

Heute läuft die Anwendung lokal über `./start.sh` (Backend + Frontend + MinIO im Docker-Container) oder `docker-compose up` (nur MinIO; Backend/Frontend lokal). Es gibt keinen reproduzierbaren Weg, sie auf AWS zu deployen.

**Ziel:** Ein Pulumi-Stack im Repo (Kotlin/JVM, Pulumi Java SDK), der die App auf AWS EKS produktiv deployt, mit S3 als persistente Storage-Schicht (ersetzt MinIO in Prod) und IRSA für sichere AWS-Zugriffe ohne statische Credentials.

**Reifegrad:** Internal-Use-Production. ALB Ingress (kein Custom-Domain, kein TLS-Cert), 2 Replicas, IRSA, Resource-Limits, Health-Checks. Keine Customer-Facing-Production-Features (WAF, ACM, Route53 → Folge-Feature).

## Getroffene Entscheidungen

| # | Frage | Entscheidung | Begründung |
|---|---|---|---|
| 1 | MVP-Scope | **Internal-Use-Production** (Variante B von 3) | Smoke-Test (A) wäre IaC-Wegwerfcode; Customer-Facing (C) ist YAGNI ohne reale Kunden |
| 2 | Stack-Aufteilung | **Zwei Pulumi-Projekte:** `productspec-base` + `productspec-workloads` | Trennung „selten geändert" / „häufig geändert"; verhindert ungewollte Cluster-Diffs bei App-Deploys; löst Henne-Ei-Problem mit Image-Push sauber |
| 3 | K8s-Workloads | **Typed Kotlin** (statt Helm-Chart oder YAML) | Nur 7 Ressourcen → ~250 Zeilen; Pulumi-Outputs fliessen typsicher ohne String-Templating; vermeidet zweites Templating-System; AWS LBC bleibt Helm |
| 4a | Pulumi State-Backend | **S3-Backend** (`s3://productspec-pulumi-state-<account>`) | Self-contained im AWS-Account, kein externer Drittanbieter |
| 4b | Image-Tagging | **Git-SHA** (`git rev-parse --short=8 HEAD`) | Immutable, Rollback trivial via `pulumi config set imageTag <old>`, idempotenter Rebuild |
| 4c | AWS-Region | **eu-central-1** (Frankfurt) | DSGVO-Default, niedrige DE-Latenz |
| 4d | Projektnamen | **`productspec-base`** und **`productspec-workloads`**, Stacks `dev` und `prod` | Konsistenz, klare Abgrenzung |
| – | Pulumi-Sprache | **Kotlin/JVM via Pulumi Java SDK** | Vom User vorgegeben; passt zum Backend-Toolchain (Java 21, Gradle) |
| – | Container-Registry | **ECR** im `productspec-base`-Stack | Same-Account, IRSA-friendly, ECR-Lifecycle-Policies |
| – | Secrets | **Pulumi Config `--secret`** → K8s Secret | Reicht für `OPENAI_API_KEY`; AWS Secrets Manager + External Secrets ist Folge-Feature |
| – | Domain/TLS | **Kein Domain-Setup**, Zugriff über generischen ALB-DNS | Folge-Feature: Route53 + ACM + Custom-Domain |

## Architektur

```
                   git push (sauberer working tree)
                      │
                      ▼
          scripts/deploy.sh <env>
          ├─ pulumi -C infra/base -s <env> up         (selten geändert)
          ├─ docker buildx + ECR push (tag = git-sha)
          └─ pulumi -C infra/workloads -s <env> up    (häufig geändert)
                      │
                      ▼
┌────────────────────── AWS Account (eu-central-1) ──────────────────────┐
│                                                                         │
│  S3: productspec-pulumi-state-<account>  ◄── Pulumi State              │
│                                                                         │
│  Stack productspec-base/{dev,prod}:                                     │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │  awsx.Vpc  →  eks.Cluster + ManagedNodeGroup + OIDC            │   │
│  │  ECR: productspec-backend, productspec-frontend                 │   │
│  │  S3: productspec-data-<env>  (versioning, AES256, no public)   │   │
│  │  IAM: backend-irsa-role  (OIDC trust → SA "backend-sa")        │   │
│  │  K8s: Namespace "productspec"                                   │   │
│  │  Helm: aws-load-balancer-controller (kube-system)              │   │
│  │  ──── Outputs: kubeconfig, oidcProviderArn, bucketName,         │   │
│  │              irsaRoleArn, ecrBackendUrl, ecrFrontendUrl,        │   │
│  │              clusterName, namespace, region                     │   │
│  └────────────────────────────────────────────────────────────────┘   │
│                              ▲                                          │
│                              │ StackReference                           │
│  Stack productspec-workloads/{dev,prod}:                                │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │  ServiceAccount backend-sa  (mit IRSA-Annotation)              │   │
│  │  Secret openai-api-key  (aus Pulumi --secret)                  │   │
│  │  Deployment backend (image: <ecrBackendUrl>:<git-sha>)          │   │
│  │  Deployment frontend (image: <ecrFrontendUrl>:<git-sha>)        │   │
│  │  Service backend, Service frontend  (ClusterIP)                │   │
│  │  Ingress productspec  (ALB, /api/* → backend, /* → frontend)   │   │
│  └────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

## Repo-Layout

```
ProductSpecAgent/
├── infra/
│   ├── base/
│   │   ├── Pulumi.yaml              # name: productspec-base, runtime: java
│   │   ├── Pulumi.dev.yaml          # aws:region eu-central-1, node-config
│   │   ├── Pulumi.prod.yaml
│   │   ├── build.gradle.kts         # Pulumi Java SDK + aws + awsx + eks + kubernetes
│   │   ├── settings.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/agentwork/infra/base/
│   │       │   ├── App.kt
│   │       │   ├── Networking.kt
│   │       │   ├── Cluster.kt
│   │       │   ├── Storage.kt
│   │       │   ├── Registry.kt
│   │       │   ├── Namespace.kt
│   │       │   └── LoadBalancerController.kt
│   │       └── test/kotlin/com/agentwork/infra/base/
│   │           └── BaseStackTest.kt
│   └── workloads/
│       ├── Pulumi.yaml              # name: productspec-workloads
│       ├── Pulumi.dev.yaml
│       ├── Pulumi.prod.yaml
│       ├── build.gradle.kts
│       ├── settings.gradle.kts
│       └── src/
│           ├── main/kotlin/com/agentwork/infra/workloads/
│           │   ├── App.kt
│           │   ├── BaseRefs.kt
│           │   ├── ServiceAccounts.kt
│           │   ├── Backend.kt
│           │   ├── Frontend.kt
│           │   └── Ingress.kt
│           └── test/kotlin/com/agentwork/infra/workloads/
│               └── WorkloadsStackTest.kt
├── scripts/
│   ├── deploy.sh                    # NEU
│   └── pulumi-bootstrap.sh          # NEU (einmalig pro AWS-Account)
├── backend/                         # bestehend (eine Datei evtl. anpassen, s. u.)
├── frontend/                        # unverändert
└── docker-compose.yml               # unverändert (lokal, MinIO)
```

`infra/base/` und `infra/workloads/` sind **zwei isolierte Gradle-Projekte** — explizit nicht im root `settings.gradle.kts`, damit `cd backend && ./gradlew test` Pulumi-frei bleibt.

## Stack `productspec-base`

### Komponenten

#### `Networking.kt`
- `awsx.ec2.Vpc("productspec-vpc")`:
  - `cidrBlock = "10.0.0.0/16"`
  - `numberOfAvailabilityZones = 3`
  - `enableDnsHostnames = true`
  - `subnetSpecs` mit Public + Private Subnets (awsx-Defaults)

#### `Cluster.kt`
- `aws.iam.Role("productspec-node-role")` mit Assume-Role-Policy für `ec2.amazonaws.com`
- 3× `aws.iam.RolePolicyAttachment`:
  - `arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy`
  - `arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy`
  - `arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly`
- `eks.Cluster("productspec-eks")`:
  - `vpcId / publicSubnetIds / privateSubnetIds` aus `Networking`
  - `skipDefaultNodeGroup = true`
  - `authenticationMode = AuthenticationMode.Api`
  - `createOidcProvider = true`
- `eks.ManagedNodeGroup("nodes")`:
  - `cluster` = obiger Cluster
  - `nodeRole` = obige Role
  - `instanceTypes` aus Pulumi-Config (`productspec-base:nodeInstanceType`)
  - `scalingConfig` aus Config (`min/desired/max`)

#### `Registry.kt`
- 2× `aws.ecr.Repository("productspec-{backend|frontend}")`:
  - `imageScanningConfiguration.scanOnPush = true`
  - `imageTagMutability = "IMMUTABLE"` (Git-SHA-Tags überschreiben sich nicht)
- 2× `aws.ecr.LifecyclePolicy`: untagged > 7 Tage löschen, max 20 tagged behalten

#### `Storage.kt`
- `aws.s3.Bucket("productspec-data-{stack}")`
- `aws.s3.BucketServerSideEncryptionConfigurationV2`: AES256
- `aws.s3.BucketVersioningV2`: enabled
- `aws.s3.BucketPublicAccessBlock`: alle 4 Toggles `true`
- `aws.iam.Role("productspec-backend-irsa")` mit OIDC-Trust-Policy:
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": { "Federated": "<oidcProviderArn>" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "<oidcProviderUrl>:sub": "system:serviceaccount:productspec:backend-sa",
          "<oidcProviderUrl>:aud": "sts.amazonaws.com"
        }
      }
    }]
  }
  ```
- `aws.iam.RolePolicy("productspec-backend-s3-access")` (inline):
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      { "Effect": "Allow", "Action": ["s3:GetObject","s3:PutObject","s3:DeleteObject"], "Resource": "<bucketArn>/*" },
      { "Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": "<bucketArn>" }
    ]
  }
  ```

#### `Namespace.kt`
- `kubernetes.Provider("k8s", kubeconfig=cluster.kubeconfig())`
- `kubernetes.core.v1.Namespace("productspec")` mit obigem Provider via `CustomResourceOptions`

#### `LoadBalancerController.kt`
- `aws.iam.Role("alb-controller-irsa")` mit OIDC-Trust für SA `aws-load-balancer-controller` im Namespace `kube-system`
- `aws.iam.RolePolicy` (inline) mit der offiziellen IAM-Policy aus dem AWS-Load-Balancer-Controller-Repo (in `LoadBalancerController.kt` als statisches JSON-Resource im Classpath)
- `kubernetes.helm.v3.Release("aws-load-balancer-controller")`:
  - `chart = "aws-load-balancer-controller"`
  - `version = "1.8.4"` (gepinnt)
  - `repositoryOpts.repo = "https://aws.github.io/eks-charts"`
  - `namespace = "kube-system"`
  - `values`:
    - `clusterName` aus Cluster-Output
    - `serviceAccount.create = true`
    - `serviceAccount.name = "aws-load-balancer-controller"`
    - `serviceAccount.annotations["eks.amazonaws.com/role-arn"]` aus IAM-Role
    - `region = "eu-central-1"`
    - `vpcId` aus VPC-Output

### Outputs

```kotlin
ctx.export("kubeconfig", cluster.kubeconfig())                           // secret
ctx.export("clusterName", cluster.eksCluster().applyValue { it.name() })
ctx.export("oidcProviderArn", cluster.core().applyValue { it.oidcProvider().arn() })
ctx.export("oidcProviderUrl", cluster.core().applyValue { it.oidcProvider().url() })
ctx.export("vpcId", vpc.vpcId())
ctx.export("bucketName", bucket.bucket())
ctx.export("bucketArn", bucket.arn())
ctx.export("backendIrsaRoleArn", backendIrsaRole.arn())
ctx.export("ecrBackendUrl", ecrBackend.repositoryUrl())
ctx.export("ecrFrontendUrl", ecrFrontend.repositoryUrl())
ctx.export("namespace", namespace.metadata().applyValue { it.name() })
ctx.export("region", Output.of(region))
```

### Stack-Config (Beispiel `Pulumi.dev.yaml`)

```yaml
config:
  aws:region: eu-central-1
  productspec-base:nodeInstanceType: t3.medium
  productspec-base:nodeMinSize: "1"
  productspec-base:nodeDesiredSize: "2"
  productspec-base:nodeMaxSize: "4"
```

`Pulumi.prod.yaml`: identisch bis auf `nodeInstanceType: t3.large`, `nodeDesiredSize: "2"`, `nodeMaxSize: "6"`.

## Stack `productspec-workloads`

### `BaseRefs.kt`

```kotlin
class BaseRefs(baseStackName: String) {
    private val ref = StackReference("productspec-base/$baseStackName")
    val kubeconfig: Output<String>         = ref.requireOutput("kubeconfig").asString()
    val namespace: Output<String>          = ref.requireOutput("namespace").asString()
    val ecrBackendUrl: Output<String>      = ref.requireOutput("ecrBackendUrl").asString()
    val ecrFrontendUrl: Output<String>     = ref.requireOutput("ecrFrontendUrl").asString()
    val backendIrsaRoleArn: Output<String> = ref.requireOutput("backendIrsaRoleArn").asString()
    val bucketName: Output<String>         = ref.requireOutput("bucketName").asString()
    val region: Output<String>             = ref.requireOutput("region").asString()
}
```

### Komponenten

#### `App.kt`
```kotlin
fun main() = Pulumi.run { ctx ->
    val cfg = ctx.config()
    val baseStack = cfg.require("baseStackName")
    val imageTag = cfg.require("imageTag")
    val openaiApiKey = cfg.requireSecret("openaiApiKey")

    val base = BaseRefs(baseStack)
    val k8sProvider = Provider("k8s", ProviderArgs.builder()
        .kubeconfig(base.kubeconfig).build())

    val sa = serviceAccount(base, k8sProvider)
    val secret = openaiSecret(base, openaiApiKey, k8sProvider)
    val backend = backend(base, imageTag, sa, secret, k8sProvider)
    val frontend = frontend(base, imageTag, k8sProvider)
    val ingress = ingress(base, backend.service, frontend.service, k8sProvider)

    ctx.export("ingressDnsName",
        ingress.status().applyValue { it.loadBalancer().ingress()[0].hostname() })
}
```

#### `ServiceAccounts.kt`
- `kubernetes.core.v1.ServiceAccount("backend-sa")`:
  - `metadata.namespace = base.namespace`
  - `metadata.annotations["eks.amazonaws.com/role-arn"] = base.backendIrsaRoleArn`
- `kubernetes.core.v1.Secret("openai-api-key")`:
  - `type = "Opaque"`
  - `stringData["OPENAI_API_KEY"] = openaiApiKey` (Pulumi-secret aus Config)
  - `metadata.namespace = base.namespace`

#### `Backend.kt`
- `kubernetes.apps.v1.Deployment("backend")`:
  - `replicas = config.requireInt("backendReplicas")` (default 2)
  - `image = Output.format("%s:%s", base.ecrBackendUrl, imageTag)`
  - `serviceAccountName = "backend-sa"`
  - `env`:
    - `S3_BUCKET` aus `base.bucketName`
    - `S3_REGION` aus `base.region`
    - `S3_PATH_STYLE = "false"`
    - `OPENAI_API_KEY` aus `secret`-Ref (`secretKeyRef`)
    - **Keine** `S3_ACCESS_KEY` / `S3_SECRET_KEY` (IRSA übernimmt)
  - `resources`:
    - `requests`: `memory=256Mi, cpu=250m`
    - `limits`: `memory=1Gi, cpu=1000m`
  - `livenessProbe`: HTTP `GET /api/health` auf Port 8080, initialDelaySeconds=30, periodSeconds=10
  - `readinessProbe`: HTTP `GET /api/health` auf Port 8080, initialDelaySeconds=5
- `kubernetes.core.v1.Service("backend")`:
  - `type = ClusterIP`
  - `port 80 → targetPort 8080`
  - `selector` matched Deployment-Labels

**Hinweis:** Die App hat bereits einen `/api/health`-Endpunkt (`HealthController.kt`), in `SecurityConfig.kt` als `permitAll()` freigegeben — direkt als K8s-Probe verwendbar. Kein Spring-Boot-Actuator nötig.

#### `Frontend.kt`
- `kubernetes.apps.v1.Deployment("frontend")`:
  - `replicas = config.requireInt("frontendReplicas")` (default 2)
  - `image = Output.format("%s:%s", base.ecrFrontendUrl, imageTag)`
  - `env`:
    - `NEXT_PUBLIC_API_URL = "/api"` (Same-Origin via ALB-Pfad-Routing)
    - `NODE_ENV = "production"`
  - `resources`:
    - `requests`: `memory=128Mi, cpu=100m`
    - `limits`: `memory=512Mi, cpu=500m`
  - `livenessProbe`: HTTP `/` auf Port 3000
  - `readinessProbe`: HTTP `/` auf Port 3000
- `kubernetes.core.v1.Service("frontend")`:
  - `type = ClusterIP`
  - `port 80 → targetPort 3000`

#### `Ingress.kt`
- `kubernetes.networking.v1.Ingress("productspec")`:
  - `metadata.annotations`:
    - `kubernetes.io/ingress.class = "alb"`
    - `alb.ingress.kubernetes.io/scheme = "internet-facing"`
    - `alb.ingress.kubernetes.io/target-type = "ip"`
    - `alb.ingress.kubernetes.io/listen-ports = "[{\"HTTP\": 80}]"`
    - `alb.ingress.kubernetes.io/healthcheck-path = "/"`
  - `spec.ingressClassName = "alb"`
  - `spec.rules[0]`:
    - `http.paths[0]`: `pathType=Prefix, path=/api, backend.service.name=backend, port.number=80`
    - `http.paths[1]`: `pathType=Prefix, path=/, backend.service.name=frontend, port.number=80`

### Outputs

```kotlin
ctx.export("ingressDnsName", ingress.status()...applyValue { it.loadBalancer().ingress()[0].hostname() })
ctx.export("backendImage", Output.format("%s:%s", base.ecrBackendUrl, imageTag))
ctx.export("frontendImage", Output.format("%s:%s", base.ecrFrontendUrl, imageTag))
```

### Stack-Config (Beispiel `Pulumi.dev.yaml`)

```yaml
config:
  aws:region: eu-central-1
  productspec-workloads:baseStackName: dev
  productspec-workloads:imageTag: placeholder        # default-Image bei erstem Apply
  productspec-workloads:openaiApiKey:
    secure: AAABA...                                  # via `pulumi config set --secret`
  productspec-workloads:backendReplicas: "2"
  productspec-workloads:frontendReplicas: "2"
```

## Backend-Code-Anpassungen

**Erfreulicherweise sind keine Code-Änderungen am Backend nötig.** Self-Review hat ergeben:

| Bedenken | Aktueller Stand | Änderung nötig? |
|---|---|---|
| `S3Config` muss IRSA unterstützen (leere Credentials → Default-Chain) | `backend/src/main/kotlin/com/agentwork/productspecagent/config/S3Config.kt` Zeile 28 prüft bereits `if (props.accessKey.isNotBlank() && props.secretKey.isNotBlank())` und setzt `StaticCredentialsProvider` nur dann. Bei leeren Werten → Default-Credential-Chain → IRSA funktioniert. | **Nein** — nur ein neuer Test als Regressionsschutz |
| K8s-Probes brauchen einen freigegebenen Health-Endpunkt | `HealthController.kt` exposed `/api/health` mit JSON `{status, timestamp}`; `SecurityConfig.kt` Zeile 20 hat ihn auf `permitAll()` | **Nein** — direkt als Probe-Pfad verwenden |
| CORS in Prod | Frontend → `/api/...` → Backend läuft same-origin via ALB-Pfad-Routing → CORS feuert nicht | **Nein** |

### Neuer Test als Regressionsschutz

`backend/src/test/kotlin/com/agentwork/productspecagent/config/S3ConfigTest.kt`:
- `s3Client built without accessKey and secretKey omits StaticCredentialsProvider()` — verifiziert via Spring-Context, dass das Bean ohne Credentials gebaut werden kann (entscheidend für IRSA-Fall im Pod)

## `scripts/deploy.sh <env>`

```bash
#!/usr/bin/env bash
set -euo pipefail
ENV="${1:?usage: deploy.sh <dev|prod>}"

# Sauberer working tree erzwingen, damit Git-SHA reproduzierbar ist
DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY" != "0" ]; then
  echo "ERROR: uncommitted changes — commit first"
  git status --short
  exit 1
fi
GIT_SHA=$(git rev-parse --short=8 HEAD)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 1) Base-Stack
pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" up --yes

# 2) ECR-URLs auslesen
ECR_BACKEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrBackendUrl)
ECR_FRONTEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrFrontendUrl)
REGION=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output region)
ECR_REGISTRY=$(echo "$ECR_BACKEND" | cut -d/ -f1)

# 3) ECR-Login
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 4) Build + Push (linux/amd64 für Apple Silicon)
docker buildx build --platform linux/amd64 \
  -t "$ECR_BACKEND:$GIT_SHA" --push "$ROOT_DIR/backend"
docker buildx build --platform linux/amd64 \
  -t "$ECR_FRONTEND:$GIT_SHA" --push "$ROOT_DIR/frontend"

# 5) Workloads-Stack
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" config set imageTag "$GIT_SHA"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" up --yes

# 6) Output
INGRESS=$(pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" stack output ingressDnsName)
echo ""
echo "=== Deploy complete ==="
echo "Image tag: $GIT_SHA"
echo "Ingress:   http://$INGRESS"
```

**Henne-Ei beim ersten Apply:** `Pulumi.{stack}.yaml` definiert `imageTag: placeholder`. Beim Stack-Init holt das Workloads-Apply ein Public-Image (`public.ecr.aws/nginx/nginx:1.27`) als Platzhalter (referenziert hartkodiert in `Backend.kt`/`Frontend.kt`, wenn `imageTag == "placeholder"`). Der erste echte `deploy.sh` ersetzt das Tag und pusht echte Images.

**Vereinfachung:** Wir verzichten auf den Placeholder-Switch und sagen im README: „Erster `deploy.sh` läuft etwa 20 Min und kann beim ersten Workloads-Apply mit `ImagePullBackOff` enden, weil ECR noch leer ist. Nach dem `docker push`-Schritt repariert sich das automatisch beim Re-Apply (oder Pod-Rolling)." Letzteres halten wir für die saubere Variante — keinen Spezialfall im Code.

**Entscheidung für die Implementation:** Variante 2 (kein Placeholder-Switch im Code, dokumentiert im README).

## `scripts/pulumi-bootstrap.sh`

```bash
#!/usr/bin/env bash
# Einmalig pro AWS-Account auszuführen, bevor irgendein `pulumi up` läuft.
set -euo pipefail
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION="eu-central-1"
STATE_BUCKET="productspec-pulumi-state-$ACCOUNT_ID"

# 1) State-Bucket
if ! aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null; then
  aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION"
  aws s3api put-bucket-versioning --bucket "$STATE_BUCKET" \
    --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption --bucket "$STATE_BUCKET" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-public-access-block --bucket "$STATE_BUCKET" \
    --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
fi

# 2) Pulumi-Login
pulumi login "s3://$STATE_BUCKET?region=$REGION"

# 3) Stacks initialisieren (idempotent)
for ENV in dev prod; do
  for PROJECT in base workloads; do
    DIR="infra/$PROJECT"
    if ! pulumi -C "$DIR" stack ls 2>/dev/null | grep -q "^$ENV"; then
      pulumi -C "$DIR" stack init "$ENV"
    fi
  done
done

echo ""
echo "=== Bootstrap complete ==="
echo "State bucket:    s3://$STATE_BUCKET"
echo "Stacks created:  productspec-base/{dev,prod}, productspec-workloads/{dev,prod}"
echo ""
echo "Next: set the OpenAI API key for each workloads stack:"
echo "  pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-..."
echo "  pulumi -C infra/workloads -s prod config set --secret openaiApiKey sk-..."
```

## Tests

### `infra/base/src/test/kotlin/.../BaseStackTest.kt`

Pulumi Java SDK enthält `com.pulumi.test.PulumiTest`-Helpers für Resource-Mocking. Tests sind reine Resource-Graph-Validierungen ohne AWS-Aufrufe.

**Geplante Test-Methoden:**

1. `stack creates vpc with cidr 10_0_0_0_16()`
2. `stack creates eks cluster with oidc enabled and api auth mode()`
3. `node role has three required managed policies attached()` — verifiziert die 3 Policy-ARNs
4. `s3 bucket has aes256 encryption versioning and public access block()`
5. `irsa role trust policy targets correct service account()` — String-Inspect der JSON-Trust-Policy
6. `aws lbc helm release uses pinned chart version 1_8_4()`
7. `stack exports all required output keys()` — Liste der Output-Namen verifizieren

### `infra/workloads/src/test/kotlin/.../WorkloadsStackTest.kt`

Mit gemockter `StackReference`, die feste Test-Outputs zurückgibt.

1. `backend deployment uses backend sa()`
2. `backend deployment image references ecr url and tag()`
3. `backend deployment env contains s3 bucket and region but no static credentials()`
4. `backend deployment liveness probe targets actuator endpoint()`
5. `openai secret contains key from pulumi config()`
6. `ingress routing maps api prefix to backend service()`
7. `ingress has alb scheme internet facing()`

### Backend-Tests

Bestehende Tests bleiben grün. Neuer Test:
- `S3ConfigTest.empty access key falls back to default credential provider()` — verifiziert, dass leere Strings nicht zu `StaticCredentialsProvider` führen.

## Pre-Flight (Dev-Maschine)

| Tool | Mindestversion | Wofür | Install (macOS) |
|---|---|---|---|
| `pulumi` | 3.150+ | Stack-Apply | `brew install pulumi/tap/pulumi` |
| `aws` CLI | 2.x | ECR-Login, Bootstrap | `brew install awscli` |
| `docker` mit `buildx` | 24+ | Cross-Platform-Build | Docker Desktop |
| `kubectl` | 1.31+ | Manuelle Cluster-Inspektion | `brew install kubectl` |
| Java 21 | — | Pulumi-Stack-Build | bereits durch Backend-Toolchain vorhanden |

`README.md` bekommt einen Abschnitt „AWS Deployment" mit:
- Pre-Flight-Tabelle
- AWS-Credential-Setup-Hinweis (`aws configure` mit Admin-Profil)
- Bootstrap-Sequenz (`scripts/pulumi-bootstrap.sh`)
- Deploy-Sequenz (`scripts/deploy.sh dev`)
- Erwartung: erster Apply ~20 min (EKS-Cluster), folgende Apply ~3 min

## Akzeptanzkriterien

1. `scripts/pulumi-bootstrap.sh` erstellt aus leerem AWS-Account: State-Bucket + 4 Pulumi-Stacks (dev/prod × base/workloads)
2. `scripts/deploy.sh dev` läuft fehlerfrei (ggf. mit dokumentiertem Re-Run nach allerersten Apply) und gibt eine ALB-DNS-URL aus
3. Aufruf der ALB-DNS auf `/` liefert das Frontend (HTTP 200, "Product Spec Agent"-Title); `/api/health` liefert 200 mit JSON `{"status":"UP","timestamp":"..."}` → Routing korrekt; `/api/v1/projects` liefert 200 mit Projekt-Liste
4. `kubectl --kubeconfig=<base-output> get pods -n productspec` zeigt 4 Pods `Running` (2× backend, 2× frontend)
5. `kubectl exec` in einen Backend-Pod und `aws sts get-caller-identity` zeigt die IRSA-Rolle (kein IAM-User)
6. Backend-Pod listet S3-Bucket via Default-Credential-Chain — `kubectl describe pod <backend>` zeigt **kein** `S3_ACCESS_KEY`-Env-Var
7. `pulumi -C infra/base -s dev preview` und `pulumi -C infra/workloads -s dev preview` zeigen je „0 changes", solange weder Code noch Config sich ändern
8. `cd infra/base && ./gradlew test` und `cd infra/workloads && ./gradlew test` grün
9. `cd backend && ./gradlew test` weiterhin grün (insbesondere neuer `S3ConfigTest`)
10. `pulumi -C infra/workloads -s dev destroy && pulumi -C infra/base -s dev destroy` räumt alles ab (außer State-Bucket selbst)
11. README.md hat „AWS Deployment"-Abschnitt
12. `docs/features/32-pulumi-aws-eks-deployment-done.md` existiert nach Abschluss

## Out of Scope (final, nicht in dieser Iteration)

- HPA / Cluster Autoscaler / Karpenter
- ACM-Cert + Route53 + Custom-Domain
- AWS Secrets Manager + External Secrets Operator
- CloudWatch-Logs/Metrics/Alarms
- CI-Pipeline (GitHub Actions) — `deploy.sh` bleibt manuelle Eintrittsstelle
- WAF, GuardDuty, AWS Backup-Lifecycle
- Spot-Instances / Reserved Capacity
- Network Policies / PSP / OPA Gatekeeper
- Service Mesh (Istio, Linkerd)
- PodDisruptionBudgets

## Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Mitigation |
|---|---|---|
| Pulumi Java SDK hinkt TS-Builder-Methoden hinterher | mittel | Bei Compile-Fehler Fallback auf JSON-String-Policies |
| `awsx.Vpc` rendert ~30 Resources → erstes Apply 8–12 min | hoch | Im README erwähnen |
| EKS-Cluster-Erstellung ~15 min beim ersten `pulumi up` | hoch | dito |
| Erstes Workloads-Apply scheitert mit `ImagePullBackOff`, weil ECR leer ist | hoch | Im README dokumentiert; nach erstem `docker push` regelt es Pod-Rolling automatisch |
| `linux/amd64`-Build auf Apple Silicon braucht QEMU | mittel | Pre-Flight-Doku |
| Helm-Chart `aws-load-balancer-controller` Version 1.8.4 könnte deprecated werden | niedrig | Pinning erlaubt explizites Version-Bump |
| Bootstrap-Skript verlangt AWS-Admin-Permissions (IAM-Rollen anlegen) | mittel | Im README dokumentieren; Day-2-Deploys brauchen weniger |
| Pulumi-State-Migration falls später auf Pulumi Cloud gewechselt wird | niedrig | `pulumi stack export ... | import ...` ist dokumentiert |

## Geänderte / neue Dateien

**Neu:**
- `infra/base/Pulumi.yaml`
- `infra/base/Pulumi.dev.yaml`
- `infra/base/Pulumi.prod.yaml`
- `infra/base/build.gradle.kts`
- `infra/base/settings.gradle.kts`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/App.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/Networking.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/Cluster.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/Storage.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/Registry.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/Namespace.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/LoadBalancerController.kt`
- `infra/base/src/main/kotlin/com/agentwork/infra/base/AlbControllerPolicy.kt` (statisches IAM-JSON aus AWS-Repo)
- `infra/base/src/test/kotlin/com/agentwork/infra/base/BaseStackTest.kt`
- `infra/workloads/Pulumi.yaml`
- `infra/workloads/Pulumi.dev.yaml`
- `infra/workloads/Pulumi.prod.yaml`
- `infra/workloads/build.gradle.kts`
- `infra/workloads/settings.gradle.kts`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/App.kt`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/BaseRefs.kt`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/ServiceAccounts.kt`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Backend.kt`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Frontend.kt`
- `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Ingress.kt`
- `infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/WorkloadsStackTest.kt`
- `scripts/deploy.sh`
- `scripts/pulumi-bootstrap.sh`

**Geändert:**
- `README.md` — neuer Abschnitt „AWS Deployment"
- `docs/features/32-pulumi-aws-eks-deployment.md` — Status auf „approved", Verweis auf diese Spec

**Neu (Backend-Test als Regressionsschutz):**
- `backend/src/test/kotlin/com/agentwork/productspecagent/config/S3ConfigTest.kt`

**Nach Abschluss neu:**
- `docs/features/32-pulumi-aws-eks-deployment-done.md`
