# Feature 32: Pulumi-Stack für AWS EKS + S3 Deployment — Done

**Status:** Implementation abgeschlossen, AWS-Apply ausstehend (User-Verantwortung)
**Datum:** 2026-04-28
**Spec:** [../superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md](../superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md)
**Plan:** [../superpowers/plans/2026-04-28-pulumi-aws-eks-deployment.md](../superpowers/plans/2026-04-28-pulumi-aws-eks-deployment.md)

## Zusammenfassung

Zwei Pulumi-Projekte in Kotlin/JVM unter `infra/base/` und `infra/workloads/` plus Bootstrap- und Deploy-Skripte. Implementation läuft komplett gegen Pulumi-Mocks (keine AWS-Calls); ein echtes `pulumi up` ist Akzeptanz-Test, nicht Implementation.

**Umgesetzt in 24 SDD-Tasks (T01–T24), 25 Commits seit Plan:**

| Phase | Tasks | Inhalt |
|---|---|---|
| 0 | T01–T02 | `infra/base/` Gradle-Skelett + Pulumi-Test-Mocks (Recorder-Pattern) |
| 1 | T03–T11 | Base-Stack: Networking (awsx VPC), EksCluster (+ NodeRole + ManagedNodeGroup), Registry (2× ECR), Storage (S3 + IRSA-Role + S3-Inline-Policy), K8sNamespace (K8s-Provider + ns), AlbControllerPolicy (Classpath-Resource), LoadBalancerController (Helm + IRSA), App.kt-Verdrahtung (12 Outputs), Stack-Configs dev/prod |
| 2 | T12–T19 | Workloads-Stack: Scaffold + Mocks, BaseRefs (StackReference-Wrapper), ServiceAccounts (SA mit IRSA-Annotation + OpenAI-Secret), Backend-Deployment+Service, Frontend-Deployment+Service, IngressModule (ALB), App.kt-Verdrahtung (Ingress-DNS-Output), Stack-Configs dev/prod |
| 3 | T20 | Backend-Regressionstest `S3ConfigTest.kt` (2 Tests: Default-Credential-Chain bei leeren Keys, StaticCredentialsProvider bei gesetzten Keys) |
| 4 | T21–T22 | `scripts/pulumi-bootstrap.sh` (S3-State-Bucket + 4 Stacks, idempotent), `scripts/deploy.sh` (base apply → ECR push → workloads apply, mit Git-SHA als Image-Tag) |
| 5 | T23–T24 | README "AWS Deployment"-Abschnitt, Feature-Doc Status auf approved |

**Test-Stand:**
- `cd infra/base && ./gradlew test` → 3 Tests grün, davon 2 sicherheits-kritisch:
  - `s3 bucket public access block has all four toggles true`
  - `irsa role trust policy targets backend-sa in productspec namespace`
- `cd infra/workloads && ./gradlew test` → 2 Tests grün, davon 1 sicherheits-kritisch:
  - `backend deployment env contains s3 bucket but no static credentials` (verifiziert IRSA-Pattern)
- `cd backend && ./gradlew test` → bestehende Suite + neue 2 `S3ConfigTest`-Tests grün

## Abweichungen vom Plan

### Pulumi-Versions-Korrekturen

Im Plan stand `pulumi:1.16.0`, `eks:3.10.0`, `awsx:2.21.1`, `kubernetes:4.21.1`. Diese Versionen existieren teilweise nicht in Maven Central. Tatsächlich verwendet (Maven-Central-verifiziert):

| Library | Plan | Realität |
|---|---|---|
| `com.pulumi:pulumi` | 1.16.0 | **1.13.2** |
| `com.pulumi:aws` | 6.83.0 | 6.83.0 (passt) |
| `com.pulumi:awsx` | 2.21.1 | **2.22.0** |
| `com.pulumi:eks` | 3.10.0 | **3.9.1** |
| `com.pulumi:kubernetes` | 4.21.1 | **4.23.0** |

Die im Plan eingebauten Phase-1-Findings (Commit `ae349ab`) reflektieren das.

### Pulumi-Java-Test-API (1.13.2)

Plan ging von eigenständigen `MockResourceArgs`/`MockCallArgs`/`NewResourceResult`/`CallResult` Klassen aus. Tatsächlich:
- Klassen heißen `Mocks.ResourceArgs`, `Mocks.ResourceResult`, `Mocks.CallArgs` (innere Klassen von `com.pulumi.test.Mocks`).
- `callAsync` returns `CompletableFuture<Map<String, Any>>` direkt (kein `CallResult`-Wrapper).
- `PulumiTest.withMocks().runTest()` ist synchron (kein `.join()`).
- `TestResult.resources()` liefert `Resource` ohne `inputs()`-Methode → **Recorder-Pattern** in `PulumiMocks` (`MutableList<Mocks.ResourceArgs>` mitprotokollieren) ist Pflicht.

### EKS-Cluster-API

- Wrapper-Class `Cluster` musste zu **`EksCluster`** umbenannt werden (Konflikt mit `com.pulumi.eks.Cluster`).
- `cluster.cluster.oidcProviderArn()`, `oidcProviderUrl()`, `kubeconfigJson()` sind **direkt `Output<String>`** — ohne `Optional`-Wrapper, ohne `core().oidcProvider().get()`-Umweg.
- `cluster.cluster.eksCluster().applyValue { it.name() }` für AWS-Cluster-Namen.
- `cluster.cluster.core().applyValue { it.vpcId() }` für VPC-ID (vpcId ist nicht-optional auf CoreData).
- `ManagedNodeGroupArgs.scalingConfig` erwartet `com.pulumi.aws.eks.inputs.NodeGroupScalingConfigArgs` (aus AWS-Provider, NICHT EKS-Provider).

### K8s-Workloads-API

- `ServiceAccountArgs.annotations()` und `SecretArgs.stringData()` erwarten `Map<String, String>` oder `Output<Map<String, String>>` — NICHT `Map<String, Output<String>>`. In T14/T17 wurden Maps via `.applyValue { }` als `Output<Map<String, String>>` materialisiert.
- `Ingress.status()` returns `Output<Optional<IngressStatus>>` (nicht `Output<IngressStatus>`) — T18 hat das mit `.applyValue { optStatus -> optStatus.orElse(null)?... }` aufgelöst.
- Wrapper-Object `IngressModule` (statt Class `Ingress`) wegen Namens-Konflikt.
- Wrapper-Class `K8sNamespace` (statt `Namespace`) wegen Namens-Konflikt.

### Helm-Chart-Version

Plan sagte AWS-LBC v2.8.4 / Helm 1.8.4. Tatsächlich: v2.8.4-Tag existiert in `kubernetes-sigs/aws-load-balancer-controller` noch nicht. Verwendet wurde **v2.8.3** (IAM-Policy-JSON aus diesem Tag) und **Helm-Chart 1.8.3**.

### Backend-Code

Plan plante zunächst Backend-Anpassungen (Spring-Boot-Actuator, CORS-Override, S3Config-Refactoring). Die Spec-Self-Review hatte das schon weggekürzt — der Code war bereits IRSA-fähig (`S3Config.kt:28` baut keinen `StaticCredentialsProvider` bei leeren Keys), `/api/health` existiert in `HealthController.kt` und ist `permitAll`'d, CORS feuert nicht weil ALB-Pfad-Routing same-origin ist. Realisiert wurde nur ein Regressionstest (`S3ConfigTest.kt`) — kein Code-Change am Backend.

### Sonstige API-Drift

- `com.pulumi.kotlin.applyValue`-Import existiert nicht — `applyValue` ist Default-Method auf `com.pulumi.core.Output<T>` und braucht keinen Import.
- `Output.tuple(a, b).applyValue { tup -> tup.t1 }` — Tupel-Properties heißen `t1`/`t2` als Property (kein Getter-Aufruf).
- Versionen-Anpassung von `t3.medium`/`t3.large`-Defaults zu Production-Pendant blieb wie geplant.

## Akzeptanzkriterien — Stand

| AK | Beschreibung | Stand |
|---|---|---|
| 1 | `scripts/pulumi-bootstrap.sh` läuft erfolgreich | **Offen** (User-Verantwortung — braucht AWS-Account-Zugang) |
| 2 | `scripts/deploy.sh dev` läuft fehlerfrei, gibt ALB-DNS aus | **Offen** (dito) |
| 3 | ALB liefert 200 auf `/`, 200 mit `{status:UP}` auf `/api/health` | **Offen** (dito) |
| 4 | `kubectl get pods -n productspec` zeigt 4 Running Pods | **Offen** (dito) |
| 5 | Backend-Pod nutzt IRSA (kein S3_ACCESS_KEY-Env-Var) | **Offen** (dito); Pulumi-Code ist so geschrieben, Test in T15 verifiziert das auf Code-Ebene |
| 6 | `pulumi destroy` räumt rückstandsfrei | **Offen** (dito) |
| 7 | `pulumi preview` zeigt 0 Changes nach `git checkout main` | **Offen** (dito) |
| 8 | `cd infra/base && ./gradlew test` grün | **Erfüllt** — 3 passing |
|   | `cd infra/workloads && ./gradlew test` grün | **Erfüllt** — 2 passing |
| 9 | `cd backend && ./gradlew test` grün (insb. `S3ConfigTest`) | **Erfüllt** |
| 10 | Tear-down via `pulumi destroy` | **Offen** (dito) |
| 11 | README hat "AWS Deployment"-Abschnitt | **Erfüllt** |
| 12 | `docs/features/32-pulumi-aws-eks-deployment-done.md` existiert | **Erfüllt** (diese Datei) |

## Offene Punkte / technische Schulden

### Akzeptanz braucht echtes AWS-Apply
Akzeptanzkriterien 1–7 und 10 setzen ein laufendes `pulumi up` gegen einen AWS-Account voraus. Das ist bewusst nicht Teil der Implementation. User-Workflow:
1. AWS-Credentials konfigurieren (`aws configure`)
2. `./scripts/pulumi-bootstrap.sh` einmalig ausführen
3. `pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-...`
4. `./scripts/deploy.sh dev`

**Erwartung beim ersten Apply:** ~20 Min (EKS-Cluster-Erstellung). Mögliche Stolperfallen siehe README.

### Pulumi-Java-API-Drift bei Updates
Da viele API-Anpassungen gegenüber dem Plan (Versions, Builder-Methods, Output-Wrappings) gefunden wurden, ist davon auszugehen, dass künftige Pulumi-Java-Provider-Updates erneut Anpassungen erfordern. Die Code-Pfade mit erhöhtem Drift-Risiko:
- `EksCluster.kt` — `ClusterArgs.builder()`, OIDC-Provider-Zugriff
- `Storage.kt` — `BucketServerSideEncryptionConfigurationV2RuleArgs`-langer-Pfad-Typ
- Workloads `*.kt` — K8s-Builder-Args (insbesondere `annotations`/`stringData`-Map-Typ)

### Helm-Chart-Version
Die hardcoded Helm-Version `1.8.3` in `LoadBalancerController.kt` muss bei zukünftigen AWS-LBC-Releases bewusst aktualisiert werden, plus die parallele IAM-Policy-Datei `alb-controller-iam-policy.json` aus dem entsprechenden Git-Tag.

### Domain / TLS / Custom-Hostname
Bewusst out-of-scope (Spec, Reifegrad B "Internal-Use-Production"). Folge-Feature: ACM-Cert + Route53-Hosted-Zone + ALB-HTTPS-Listener. Die Ingress-Annotations in T17 sind so gesetzt, dass HTTPS additive ergänzt werden kann (`alb.ingress.kubernetes.io/listen-ports` von `[{HTTP:80}]` auf `[{HTTP:80},{HTTPS:443}]` erweitern, `certificate-arn`-Annotation hinzufügen).

### AWS-Secrets-Manager + External-Secrets-Operator
Out-of-scope. Aktuell wandert `OPENAI_API_KEY` als Pulumi-secret in den Stack-State (KMS-encrypted) und als K8s-Secret (etcd, base64). Folge-Feature: External Secrets Operator + AWS Secrets Manager.

### `imageTag: placeholder` als Default in Stack-Config
Bei manueller `pulumi up infra/workloads` ohne vorherigen `deploy.sh`-Lauf würde der Stack ein nicht existentes Image referenzieren (`<ecr-url>:placeholder`) und mit `ImagePullBackOff` fehlschlagen. Im normalen `deploy.sh`-Flow wird `imageTag` vor dem Apply gesetzt — kein Problem. README dokumentiert das.

### Tests inspizieren Pulumi-internal-input-Repräsentation
T06 und T15 navigieren tief in `Mocks.ResourceArgs.inputs` (nested Maps). Bei zukünftigen Pulumi-Java-Updates kann sich die Input-Map-Struktur ändern (z. B. wenn Pulumi K8s-Resources stärker JSON-serialisiert). Falls Tests dann brechen: Fallback ist String-Smoke-Test auf `inputs.toString()`. Bereits in T15-Implementer-Briefing als Backup dokumentiert.

### `docker-compose.yml` unstaged
Eine pre-existing Änderung an `docker-compose.yml` aus Feature 31 ist seit Beginn unstaged und wurde in keinem Commit dieses Features mitgenommen. User entscheidet separat über deren Schicksal.

## Commit-Historie

```
3cc0e38 docs(feature-32): mark spec as approved and link plan
7ff0a0a docs(infra-32): document AWS deployment workflow
5cbf74c feat(scripts-32): add deploy.sh for AWS EKS rollout
85b1e4a feat(scripts-32): add one-time Pulumi bootstrap script
9f13699 test(infra-32): cover IRSA-friendly S3Client construction
7785042 feat(infra-32): add workloads stack configs for dev and prod
e0819b6 feat(infra-32): wire workloads stack and export ingress dns
660dc4b feat(infra-32): add ALB Ingress with path-based routing
45817aa feat(infra-32): add frontend deployment + service
f75ddac feat(infra-32): add backend deployment + service with IRSA test
af25ddf feat(infra-32): add backend ServiceAccount + openai Secret
cec360b feat(infra-32): add typed BaseRefs wrapper for StackReference
b9d2d66 feat(infra-32): scaffold Pulumi workloads project
ae349ab docs(feature-32): apply phase 1 implementation findings to plan
14f2425 feat(infra-32): add base stack configs for dev and prod
a55c14f feat(infra-32): wire base stack and export outputs
9a61268 feat(infra-32): add AWS Load Balancer Controller via Helm with IRSA
6047636 feat(infra-32): add AWS LBC IAM policy as classpath resource
29473f7 feat(infra-32): add K8s provider and productspec namespace
0ba4299 feat(infra-32): add S3 bucket + IRSA role with TDD coverage
641563d feat(infra-32): add ECR repositories with lifecycle policies
c80762c feat(infra-32): add EKS cluster + node group module
f14003e feat(infra-32): add VPC module to base stack
e05b6cb test(infra-32): add Pulumi mocks and base stack test scaffold
4b1f0dd feat(infra-32): scaffold Pulumi base project (Kotlin/Gradle)
d529f6f docs(feature-32): add implementation plan for Pulumi AWS EKS deployment
1da5c34 docs(feature-32): add Pulumi AWS EKS + S3 deployment spec
```
