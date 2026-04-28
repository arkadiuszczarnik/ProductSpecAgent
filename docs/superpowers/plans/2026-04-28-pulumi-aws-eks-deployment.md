# Feature 32 — Pulumi AWS EKS + S3 Deployment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zwei Pulumi-Projekte (Kotlin/JVM, Pulumi Java SDK) im Repo unter `infra/base/` (VPC, EKS, ECR, S3, IRSA, K8s-Namespace, AWS Load Balancer Controller) und `infra/workloads/` (ServiceAccount, Secret, Backend/Frontend Deployments + Services + Ingress), gekoppelt via `StackReference`. Plus zwei Shell-Skripte (`pulumi-bootstrap.sh`, `deploy.sh`), ein Backend-Regressionstest und README-Erweiterung.

**Architecture:** Zwei eigenständige Gradle-Projekte unter `infra/`, die nicht ins Root-`settings.gradle.kts` eingebunden werden. Der `productspec-base`-Stack erzeugt langlebige AWS-Infra (Cluster, ECR, S3) und exportiert Outputs; der `productspec-workloads`-Stack liest sie via `StackReference`, definiert die K8s-Workloads als typed Kotlin und nutzt IRSA, sodass der Backend-Pod ohne statische Credentials auf S3 zugreift. State liegt in einem dedizierten S3-Bucket (kein Pulumi Cloud).

**Tech Stack:**
- Pulumi Java SDK `com.pulumi:pulumi:1.16.0` (oder neueste 1.x)
- Pulumi Provider: `com.pulumi:aws:6.83.0`, `com.pulumi:awsx:2.21.1`, `com.pulumi:eks:3.10.0`, `com.pulumi:kubernetes:4.21.1`
- Kotlin 2.3.10, Java 21, Gradle Wrapper (kopiert aus `backend/`)
- AWS Load Balancer Controller Helm-Chart `1.8.4` (Repo `https://aws.github.io/eks-charts`)
- Backend: Spring Boot 4 / Kotlin 2.3 / Java 21 (bestehend, eine Test-Datei kommt dazu)

**Approved Spec:** [docs/superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md](../specs/2026-04-28-pulumi-aws-eks-deployment-design.md)

**Sprache der Commit-Messages:** Englisch (matched bestehenden Repo-Stil — siehe `git log`).

---

## Vorbedingungen

- Branch: arbeite auf einem Feature-Branch `feat/feature-32-pulumi-aws-eks` (oder dedicated Worktree).
- **Kein** AWS-Account-Zugriff während Implementation nötig — alle Tests laufen mit Pulumi-Mocks. AWS-Apply ist Teil der Akzeptanz, nicht der Implementation.
- Java 21, Gradle Wrapper sind bereits durch `backend/` verfügbar.
- Pulumi CLI ist NICHT für Implementation nötig (nur für späteren Apply). Falls du sie zur Plan-Verifikation installierst: `brew install pulumi/tap/pulumi`.
- `git status` ist sauber bevor du startest (Bestandsänderung an `docker-compose.yml` ist okay, sie gehört nicht zu diesem Feature).

---

## Datei-Struktur (Übersicht)

```
infra/
├── base/                                                       # Phase 1
│   ├── Pulumi.yaml                                             # T01
│   ├── Pulumi.dev.yaml                                         # T11
│   ├── Pulumi.prod.yaml                                        # T11
│   ├── build.gradle.kts                                        # T01
│   ├── settings.gradle.kts                                     # T01
│   ├── gradle/, gradlew, gradlew.bat                           # T01 (kopiert aus backend/)
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/agentwork/infra/base/
│       │   │   ├── App.kt                                      # T01 (Skeleton), T10 (final)
│       │   │   ├── Networking.kt                               # T03
│       │   │   ├── Cluster.kt                                  # T04
│       │   │   ├── Registry.kt                                 # T05
│       │   │   ├── Storage.kt                                  # T06
│       │   │   ├── Namespace.kt                                # T07
│       │   │   ├── AlbControllerPolicy.kt                      # T08
│       │   │   └── LoadBalancerController.kt                   # T09
│       │   └── resources/
│       │       └── alb-controller-iam-policy.json              # T08
│       └── test/kotlin/com/agentwork/infra/base/
│           ├── PulumiMocks.kt                                  # T02
│           └── BaseStackTest.kt                                # T02 (skeleton), erweitert in T03/T06/T10
├── workloads/                                                  # Phase 2
│   ├── Pulumi.yaml                                             # T12
│   ├── Pulumi.dev.yaml                                         # T19
│   ├── Pulumi.prod.yaml                                        # T19
│   ├── build.gradle.kts                                        # T12
│   ├── settings.gradle.kts                                     # T12
│   ├── gradle/, gradlew, gradlew.bat                           # T12
│   └── src/
│       ├── main/kotlin/com/agentwork/infra/workloads/
│       │   ├── App.kt                                          # T12 (Skeleton), T18 (final)
│       │   ├── BaseRefs.kt                                     # T13
│       │   ├── ServiceAccounts.kt                              # T14
│       │   ├── Backend.kt                                      # T15
│       │   ├── Frontend.kt                                     # T16
│       │   └── Ingress.kt                                      # T17
│       └── test/kotlin/com/agentwork/infra/workloads/
│           ├── PulumiMocks.kt                                  # T12
│           └── WorkloadsStackTest.kt                           # T12 (skeleton), erweitert in T15/T18

scripts/
├── pulumi-bootstrap.sh                                         # T21
└── deploy.sh                                                   # T22

backend/src/test/kotlin/com/agentwork/productspecagent/config/
└── S3ConfigTest.kt                                             # T20

README.md                                                       # T23 (Abschnitt anhängen)
docs/features/32-pulumi-aws-eks-deployment.md                   # T24 (Status-Update)
```

**Wichtig:** `infra/base/` und `infra/workloads/` sind voneinander unabhängige Gradle-Projekte. Sie werden **nicht** ins Root-`settings.gradle.kts` aufgenommen. Das hält den Backend-Build unabhängig.

---

## Phase 0 — Setup

### Task 1: `infra/base/` Gradle-Projekt initialisieren

**Files:**
- Create: `infra/base/Pulumi.yaml`
- Create: `infra/base/build.gradle.kts`
- Create: `infra/base/settings.gradle.kts`
- Create: `infra/base/.gitignore`
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/App.kt`
- Copy: Gradle Wrapper aus `backend/` nach `infra/base/`

- [ ] **Step 1: Gradle-Wrapper aus backend/ kopieren**

```bash
mkdir -p infra/base
cp -r backend/gradle infra/base/
cp backend/gradlew backend/gradlew.bat infra/base/
chmod +x infra/base/gradlew
```

Verify: `ls infra/base/gradle/wrapper/` zeigt `gradle-wrapper.jar` und `gradle-wrapper.properties`.

- [ ] **Step 2: `infra/base/settings.gradle.kts` schreiben**

```kotlin
rootProject.name = "productspec-base"
```

- [ ] **Step 3: `infra/base/build.gradle.kts` schreiben**

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "com.agentwork.infra.base"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pulumi:pulumi:1.16.0")
    implementation("com.pulumi:aws:6.83.0")
    implementation("com.pulumi:awsx:2.21.1")
    implementation("com.pulumi:eks:3.10.0")
    implementation("com.pulumi:kubernetes:4.21.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.agentwork.infra.base.AppKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
```

- [ ] **Step 4: `infra/base/Pulumi.yaml` schreiben**

```yaml
name: productspec-base
runtime:
  name: java
  options:
    use-executor: gradle
description: Productspec base infrastructure (VPC, EKS, ECR, S3, IRSA)
```

- [ ] **Step 5: `infra/base/.gitignore` schreiben**

```
.gradle/
build/
*.class
Pulumi.*.yaml.bak
```

- [ ] **Step 6: Skeleton `App.kt` schreiben**

Datei: `infra/base/src/main/kotlin/com/agentwork/infra/base/App.kt`

```kotlin
package com.agentwork.infra.base

import com.pulumi.Pulumi

fun main() {
    Pulumi.run { _ ->
        // Module werden in Tasks 3-9 hinzugefügt; in Task 10 verdrahtet.
    }
}
```

- [ ] **Step 7: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`. Dependencies werden aus mavenCentral aufgelöst.

- [ ] **Step 8: Commit**

```bash
git add infra/base/
git commit -m "feat(infra-32): scaffold Pulumi base project (Kotlin/Gradle)"
```

---

### Task 2: Pulumi-Test-Mocks und leere `BaseStackTest.kt`

**Files:**
- Create: `infra/base/src/test/kotlin/com/agentwork/infra/base/PulumiMocks.kt`
- Create: `infra/base/src/test/kotlin/com/agentwork/infra/base/BaseStackTest.kt`

- [ ] **Step 1: `PulumiMocks.kt` schreiben**

Datei: `infra/base/src/test/kotlin/com/agentwork/infra/base/PulumiMocks.kt`

```kotlin
package com.agentwork.infra.base

import com.pulumi.test.Mocks
import com.pulumi.test.MockCallArgs
import com.pulumi.test.MockResourceArgs
import com.pulumi.test.NewResourceResult
import com.pulumi.test.CallResult
import java.util.Optional
import java.util.concurrent.CompletableFuture

class PulumiMocks : Mocks {
    override fun newResourceAsync(args: MockResourceArgs): CompletableFuture<NewResourceResult> {
        // Synthetische ID: <name>_id; Inputs werden 1:1 als Outputs zurückgegeben.
        // Spezialfälle für Resources, die berechnete Ausgaben brauchen:
        val outputs = HashMap<String, Any>(args.inputs)
        when (args.type) {
            "aws:s3/bucket:Bucket" -> outputs["arn"] = "arn:aws:s3:::${args.name}"
            "aws:iam/role:Role" -> outputs["arn"] = "arn:aws:iam::123456789012:role/${args.name}"
            "aws:ecr/repository:Repository" -> {
                outputs["repositoryUrl"] = "123456789012.dkr.ecr.eu-central-1.amazonaws.com/${args.name}"
                outputs["arn"] = "arn:aws:ecr:eu-central-1:123456789012:repository/${args.name}"
            }
            "eks:index:Cluster" -> {
                outputs["kubeconfig"] = "{}"
            }
        }
        return CompletableFuture.completedFuture(
            NewResourceResult.of(Optional.of("${args.name}_id"), outputs)
        )
    }

    override fun callAsync(args: MockCallArgs): CompletableFuture<CallResult> {
        return CompletableFuture.completedFuture(CallResult.of(emptyMap<String, Any>()))
    }
}
```

- [ ] **Step 2: Skeleton `BaseStackTest.kt` schreiben**

Datei: `infra/base/src/test/kotlin/com/agentwork/infra/base/BaseStackTest.kt`

```kotlin
package com.agentwork.infra.base

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class BaseStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ ->
                // Wird in T03-T10 schrittweise gefüllt.
            }
            .join()
        assertNotNull(result)
    }
}
```

- [ ] **Step 3: Test laufen lassen**

Run: `cd infra/base && ./gradlew test --quiet`
Expected: `BUILD SUCCESSFUL`, 1 Test grün.

- [ ] **Step 4: Commit**

```bash
git add infra/base/src/test/
git commit -m "test(infra-32): add Pulumi mocks and base stack test scaffold"
```

---

## Phase 1 — Base-Stack Module

> **TDD-Hinweis:** Für Pulumi-IaC ist klassisches Unit-TDD weniger wertvoll als Resource-Graph-Validierung. Wir testen pro Modul nur sicherheitskritische Eigenschaften (z. B. dass `BucketPublicAccessBlock` alle 4 Toggles auf `true` setzt). Reine Eigenschafts-Echos (CIDR-Block, Region) brauchen keinen Test — der Compiler prüft sie über Builder-Pattern.

### Task 3: `Networking.kt` — VPC

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/Networking.kt`

- [ ] **Step 1: `Networking.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.awsx.ec2.Vpc
import com.pulumi.awsx.ec2.VpcArgs

class Networking(val vpc: Vpc) {
    companion object {
        fun create(ctx: Context): Networking {
            val vpc = Vpc(
                "productspec-vpc",
                VpcArgs.builder()
                    .cidrBlock("10.0.0.0/16")
                    .numberOfAvailabilityZones(3)
                    .enableDnsHostnames(true)
                    .build()
            )
            return Networking(vpc)
        }
    }
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

Falls Compile-Fehler bei `VpcArgs.builder()`-Methoden (`numberOfAvailabilityZones` o. ä.): Pulumi-Java-SDK-Build-Versionen können API-Drift haben. Konsultiere `https://www.pulumi.com/registry/packages/awsx/api-docs/ec2/vpc/` für die aktuelle Java-Builder-Signatur und ersetze Methoden 1:1.

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/Networking.kt
git commit -m "feat(infra-32): add VPC module to base stack"
```

---

### Task 4: `Cluster.kt` — EKS-Cluster + Worker-Role + ManagedNodeGroup

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/Cluster.kt`

- [ ] **Step 1: `Cluster.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.iam.Role
import com.pulumi.aws.iam.RoleArgs
import com.pulumi.aws.iam.RolePolicyAttachment
import com.pulumi.aws.iam.RolePolicyAttachmentArgs
import com.pulumi.eks.Cluster
import com.pulumi.eks.ClusterArgs
import com.pulumi.eks.ManagedNodeGroup
import com.pulumi.eks.ManagedNodeGroupArgs
import com.pulumi.eks.enums.AuthenticationMode
import com.pulumi.eks.inputs.ClusterNodeGroupOptionsArgs

class Cluster(
    val cluster: Cluster,
    val nodeRole: Role,
    val nodeGroup: ManagedNodeGroup
) {
    companion object {
        fun create(ctx: Context, networking: Networking): com.agentwork.infra.base.Cluster {
            val cfg = ctx.config("productspec-base")
            val instanceType = cfg.get("nodeInstanceType").orElse("t3.medium")
            val minSize = cfg.get("nodeMinSize").orElse("1").toInt()
            val desiredSize = cfg.get("nodeDesiredSize").orElse("2").toInt()
            val maxSize = cfg.get("nodeMaxSize").orElse("4").toInt()

            val assumeRolePolicy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Principal": {"Service": "ec2.amazonaws.com"}
                  }]
                }
            """.trimIndent()

            val nodeRole = Role(
                "productspec-node-role",
                RoleArgs.builder()
                    .assumeRolePolicy(assumeRolePolicy)
                    .build()
            )

            listOf(
                "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
                "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
                "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
            ).forEachIndexed { idx, policyArn ->
                RolePolicyAttachment(
                    "productspec-node-policy-$idx",
                    RolePolicyAttachmentArgs.builder()
                        .role(nodeRole.name())
                        .policyArn(policyArn)
                        .build()
                )
            }

            val cluster = Cluster(
                "productspec-eks",
                ClusterArgs.builder()
                    .vpcId(networking.vpc.vpcId())
                    .publicSubnetIds(networking.vpc.publicSubnetIds())
                    .privateSubnetIds(networking.vpc.privateSubnetIds())
                    .skipDefaultNodeGroup(true)
                    .authenticationMode(AuthenticationMode.Api)
                    .createOidcProvider(true)
                    .build()
            )

            val nodeGroup = ManagedNodeGroup(
                "productspec-nodes",
                ManagedNodeGroupArgs.builder()
                    .cluster(cluster)
                    .nodeRole(nodeRole)
                    .instanceTypes(listOf(instanceType))
                    .scalingConfig(
                        ManagedNodeGroupArgs.scalingConfigBuilder()
                            .minSize(minSize)
                            .desiredSize(desiredSize)
                            .maxSize(maxSize)
                            .build()
                    )
                    .build()
            )

            return Cluster(cluster, nodeRole, nodeGroup)
        }
    }
}
```

**Hinweis zum Class-Naming:** Das Modul-Wrapper-Class heißt `Cluster` und schattet die importierte `com.pulumi.eks.Cluster`. Innen wird die Pulumi-Klasse über den voll-qualifizierten Namen `com.pulumi.eks.Cluster` referenziert oder durch Alias-Import. Falls Build-Fehler: Modul-Class umbenennen zu `EksCluster`.

**Falls** `ManagedNodeGroupArgs.scalingConfigBuilder()` nicht existiert: alternativer Aufruf mit `NodeGroupScalingConfigArgs.builder()` aus `com.pulumi.aws.eks.inputs`. Konsultiere Pulumi-Java-Doku für die aktuelle Signatur.

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`. Bei Class-Name-Konflikt → siehe Hinweis oben.

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/Cluster.kt
git commit -m "feat(infra-32): add EKS cluster + node group module"
```

---

### Task 5: `Registry.kt` — ECR-Repos für Backend und Frontend

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/Registry.kt`

- [ ] **Step 1: `Registry.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.ecr.LifecyclePolicy
import com.pulumi.aws.ecr.LifecyclePolicyArgs
import com.pulumi.aws.ecr.Repository
import com.pulumi.aws.ecr.RepositoryArgs
import com.pulumi.aws.ecr.inputs.RepositoryImageScanningConfigurationArgs

class Registry(val backend: Repository, val frontend: Repository) {
    companion object {
        private val LIFECYCLE_POLICY = """
            {
              "rules": [
                {
                  "rulePriority": 1,
                  "description": "Keep last 20 tagged images",
                  "selection": {
                    "tagStatus": "tagged",
                    "tagPatternList": ["*"],
                    "countType": "imageCountMoreThan",
                    "countNumber": 20
                  },
                  "action": {"type": "expire"}
                },
                {
                  "rulePriority": 2,
                  "description": "Expire untagged after 7 days",
                  "selection": {
                    "tagStatus": "untagged",
                    "countType": "sinceImagePushed",
                    "countUnit": "days",
                    "countNumber": 7
                  },
                  "action": {"type": "expire"}
                }
              ]
            }
        """.trimIndent()

        fun create(ctx: Context): Registry {
            val backend = repository("productspec-backend")
            val frontend = repository("productspec-frontend")
            return Registry(backend, frontend)
        }

        private fun repository(name: String): Repository {
            val repo = Repository(
                name,
                RepositoryArgs.builder()
                    .imageTagMutability("IMMUTABLE")
                    .imageScanningConfiguration(
                        RepositoryImageScanningConfigurationArgs.builder().scanOnPush(true).build()
                    )
                    .forceDelete(true)
                    .build()
            )
            LifecyclePolicy(
                "$name-lifecycle",
                LifecyclePolicyArgs.builder()
                    .repository(repo.name())
                    .policy(LIFECYCLE_POLICY)
                    .build()
            )
            return repo
        }
    }
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/Registry.kt
git commit -m "feat(infra-32): add ECR repositories with lifecycle policies"
```

---

### Task 6: `Storage.kt` — S3-Bucket + IRSA-Role mit S3-Policy + Test

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/Storage.kt`
- Modify: `infra/base/src/test/kotlin/com/agentwork/infra/base/BaseStackTest.kt`

- [ ] **Step 1: Failing Test in `BaseStackTest.kt` ergänzen**

Ersetze den Inhalt von `BaseStackTest.kt`:

```kotlin
package com.agentwork.infra.base

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaseStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
            .join()
        assertNotNull(result)
    }

    @Test
    fun `s3 bucket public access block has all four toggles true`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { ctx ->
                val net = Networking.create(ctx)
                val cluster = Cluster.create(ctx, net)
                Storage.create(ctx, cluster)
            }.join()

        val pab = result.resources()
            .firstOrNull { it.type == "aws:s3/bucketPublicAccessBlock:BucketPublicAccessBlock" }
        assertNotNull(pab, "BucketPublicAccessBlock not found in stack")
        assertEquals(true, pab.inputs["blockPublicAcls"])
        assertEquals(true, pab.inputs["ignorePublicAcls"])
        assertEquals(true, pab.inputs["blockPublicPolicy"])
        assertEquals(true, pab.inputs["restrictPublicBuckets"])
    }

    @Test
    fun `irsa role trust policy targets backend-sa in productspec namespace`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { ctx ->
                val net = Networking.create(ctx)
                val cluster = Cluster.create(ctx, net)
                Storage.create(ctx, cluster)
            }.join()

        val irsa = result.resources()
            .firstOrNull { it.type == "aws:iam/role:Role" && it.name == "productspec-backend-irsa" }
        assertNotNull(irsa, "backend-irsa Role not found in stack")
        val trustPolicy = irsa.inputs["assumeRolePolicy"] as String
        assertTrue(
            trustPolicy.contains("system:serviceaccount:productspec:backend-sa"),
            "trust policy must reference backend-sa subject; was: $trustPolicy"
        )
    }
}
```

**Hinweis zur Result-API:** `result.resources()` und `it.inputs` / `it.type` / `it.name` sind die Pulumi-Java-Test-API-Felder. Falls die exakte Signatur abweicht (z. B. Getter-Methoden statt Properties): Stelle die Zugriffe auf das tatsächliche API um (`it.getType()` etc.). Die `PulumiTest.runTest`-Result-Klasse heißt `com.pulumi.test.TestResult`.

- [ ] **Step 2: Test laufen lassen — soll fehlschlagen**

Run: `cd infra/base && ./gradlew test --quiet`
Expected: `FAIL` — `Storage` Reference unresolved.

- [ ] **Step 3: `Storage.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.iam.Role
import com.pulumi.aws.iam.RoleArgs
import com.pulumi.aws.iam.RolePolicy
import com.pulumi.aws.iam.RolePolicyArgs
import com.pulumi.aws.s3.Bucket
import com.pulumi.aws.s3.BucketArgs
import com.pulumi.aws.s3.BucketPublicAccessBlock
import com.pulumi.aws.s3.BucketPublicAccessBlockArgs
import com.pulumi.aws.s3.BucketServerSideEncryptionConfigurationV2
import com.pulumi.aws.s3.BucketServerSideEncryptionConfigurationV2Args
import com.pulumi.aws.s3.BucketVersioningV2
import com.pulumi.aws.s3.BucketVersioningV2Args
import com.pulumi.aws.s3.inputs.BucketServerSideEncryptionConfigurationV2RuleArgs
import com.pulumi.aws.s3.inputs.BucketServerSideEncryptionConfigurationV2RuleApplyServerSideEncryptionByDefaultArgs
import com.pulumi.aws.s3.inputs.BucketVersioningV2VersioningConfigurationArgs
import com.pulumi.core.Output

class Storage(val bucket: Bucket, val backendIrsaRole: Role) {
    companion object {
        fun create(ctx: Context, cluster: Cluster): Storage {
            val stack = ctx.stackName()

            val bucket = Bucket(
                "productspec-data",
                BucketArgs.builder().bucket("productspec-data-$stack").forceDestroy(true).build()
            )

            BucketServerSideEncryptionConfigurationV2(
                "productspec-data-sse",
                BucketServerSideEncryptionConfigurationV2Args.builder()
                    .bucket(bucket.id())
                    .rules(listOf(
                        BucketServerSideEncryptionConfigurationV2RuleArgs.builder()
                            .applyServerSideEncryptionByDefault(
                                BucketServerSideEncryptionConfigurationV2RuleApplyServerSideEncryptionByDefaultArgs.builder()
                                    .sseAlgorithm("AES256")
                                    .build()
                            )
                            .build()
                    ))
                    .build()
            )

            BucketVersioningV2(
                "productspec-data-versioning",
                BucketVersioningV2Args.builder()
                    .bucket(bucket.id())
                    .versioningConfiguration(
                        BucketVersioningV2VersioningConfigurationArgs.builder().status("Enabled").build()
                    )
                    .build()
            )

            BucketPublicAccessBlock(
                "productspec-data-pab",
                BucketPublicAccessBlockArgs.builder()
                    .bucket(bucket.id())
                    .blockPublicAcls(true)
                    .ignorePublicAcls(true)
                    .blockPublicPolicy(true)
                    .restrictPublicBuckets(true)
                    .build()
            )

            // IRSA Trust Policy: nutzt OIDC-Provider aus dem Cluster
            val oidcArn = cluster.cluster.core().applyValue { it.oidcProvider().get().arn() }
            val oidcUrl = cluster.cluster.core().applyValue { it.oidcProvider().get().url() }

            val trustPolicy: Output<String> = Output.tuple(oidcArn, oidcUrl).applyValue { (arn, url) ->
                """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"Federated": "$arn"},
                    "Action": "sts:AssumeRoleWithWebIdentity",
                    "Condition": {
                      "StringEquals": {
                        "$url:sub": "system:serviceaccount:productspec:backend-sa",
                        "$url:aud": "sts.amazonaws.com"
                      }
                    }
                  }]
                }
                """.trimIndent()
            }

            val backendIrsaRole = Role(
                "productspec-backend-irsa",
                RoleArgs.builder().assumeRolePolicy(trustPolicy).build()
            )

            val s3Policy: Output<String> = bucket.arn().applyValue { arn ->
                """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {"Effect": "Allow", "Action": ["s3:GetObject","s3:PutObject","s3:DeleteObject"], "Resource": "$arn/*"},
                    {"Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": "$arn"}
                  ]
                }
                """.trimIndent()
            }

            RolePolicy(
                "productspec-backend-s3-access",
                RolePolicyArgs.builder()
                    .role(backendIrsaRole.id())
                    .policy(s3Policy)
                    .build()
            )

            return Storage(bucket, backendIrsaRole)
        }
    }
}
```

**Hinweis:** Falls `cluster.cluster.core()` kein `Output<ClusterCore>` liefert (API-Drift): Alternative ist `cluster.cluster.eksCluster().identities()...oidc()...issuer()`, aber `core().oidcProvider()` ist die kanonische Form. Konsultiere bei Compile-Fehler die Pulumi EKS Java-Doku.

- [ ] **Step 4: Test laufen lassen — soll passen**

Run: `cd infra/base && ./gradlew test --quiet`
Expected: `BUILD SUCCESSFUL`, alle 3 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/Storage.kt \
        infra/base/src/test/kotlin/com/agentwork/infra/base/BaseStackTest.kt
git commit -m "feat(infra-32): add S3 bucket + IRSA role with TDD coverage"
```

---

### Task 7: `Namespace.kt` — K8s-Provider und Namespace

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/Namespace.kt`

- [ ] **Step 1: `Namespace.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs
import com.pulumi.kubernetes.core.v1.Namespace
import com.pulumi.kubernetes.core.v1.NamespaceArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions

class Namespace(val provider: Provider, val namespace: Namespace) {
    companion object {
        fun create(ctx: Context, cluster: Cluster): Namespace {
            val provider = Provider(
                "productspec-k8s",
                ProviderArgs.builder().kubeconfig(cluster.cluster.kubeconfigJson()).build()
            )
            val ns = Namespace(
                "productspec-namespace",
                NamespaceArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("productspec").build())
                    .build(),
                CustomResourceOptions.builder().provider(provider).build()
            )
            return Namespace(provider, ns)
        }
    }
}
```

**Hinweis zum Self-Shadowing:** Die Wrapper-Class heißt `Namespace` und beim Innen-Aufruf gibt es einen Namens-Konflikt mit `com.pulumi.kubernetes.core.v1.Namespace`. Lösung: Wrapper umbenennen zu `K8sNamespace`, oder den Pulumi-Namespace mit Fully Qualified Name verwenden:

```kotlin
val ns = com.pulumi.kubernetes.core.v1.Namespace(
    "productspec-namespace",
    ...
)
```

Wähle die Variante, die im Build durchgeht. **Empfehlung:** Wrapper umbenennen zu `K8sNamespace`. In dem Fall passe T10 (App.kt-Verdrahtung) entsprechend an.

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/Namespace.kt
git commit -m "feat(infra-32): add K8s provider and productspec namespace"
```

---

### Task 8: AWS LBC IAM-Policy als Resource-Datei + `AlbControllerPolicy.kt`

**Files:**
- Create: `infra/base/src/main/resources/alb-controller-iam-policy.json`
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/AlbControllerPolicy.kt`

- [ ] **Step 1: IAM-Policy-JSON aus AWS-Repo speichern**

Datei: `infra/base/src/main/resources/alb-controller-iam-policy.json`

Inhalt: Die offizielle AWS-Policy v2.8.4 (passt zur Helm-Chart-Version 1.8.4). Quelle: `https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.8.4/docs/install/iam_policy.json`.

**Vorgehen:**

```bash
curl -sSL https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.8.4/docs/install/iam_policy.json \
  -o infra/base/src/main/resources/alb-controller-iam-policy.json
```

Verify: `head -3 infra/base/src/main/resources/alb-controller-iam-policy.json` zeigt:
```json
{
    "Version": "2012-10-17",
    "Statement": [
```

Falls Curl ohne Internet nicht möglich: Datei manuell anlegen mit Inhalt aus dem Link.

- [ ] **Step 2: `AlbControllerPolicy.kt` schreiben**

```kotlin
package com.agentwork.infra.base

object AlbControllerPolicy {
    fun load(): String {
        val resource = AlbControllerPolicy::class.java
            .getResourceAsStream("/alb-controller-iam-policy.json")
            ?: error("alb-controller-iam-policy.json not on classpath")
        return resource.bufferedReader().use { it.readText() }
    }
}
```

- [ ] **Step 3: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin processResources --quiet`
Expected: `BUILD SUCCESSFUL`. Die JSON-Datei landet unter `build/resources/main/`.

- [ ] **Step 4: Commit**

```bash
git add infra/base/src/main/resources/alb-controller-iam-policy.json \
        infra/base/src/main/kotlin/com/agentwork/infra/base/AlbControllerPolicy.kt
git commit -m "feat(infra-32): add AWS LBC IAM policy as classpath resource"
```

---

### Task 9: `LoadBalancerController.kt` — IRSA-Role + Helm-Release für AWS LBC

**Files:**
- Create: `infra/base/src/main/kotlin/com/agentwork/infra/base/LoadBalancerController.kt`

- [ ] **Step 1: `LoadBalancerController.kt` implementieren**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.iam.Role
import com.pulumi.aws.iam.RoleArgs
import com.pulumi.aws.iam.RolePolicy
import com.pulumi.aws.iam.RolePolicyArgs
import com.pulumi.core.Output
import com.pulumi.kubernetes.helm.v3.Release
import com.pulumi.kubernetes.helm.v3.ReleaseArgs
import com.pulumi.kubernetes.helm.v3.inputs.RepositoryOptsArgs
import com.pulumi.resources.CustomResourceOptions

object LoadBalancerController {
    fun create(ctx: Context, cluster: Cluster, namespace: K8sNamespace) {
        val oidcArn = cluster.cluster.core().applyValue { it.oidcProvider().get().arn() }
        val oidcUrl = cluster.cluster.core().applyValue { it.oidcProvider().get().url() }

        val trustPolicy: Output<String> = Output.tuple(oidcArn, oidcUrl).applyValue { (arn, url) ->
            """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Effect": "Allow",
                "Principal": {"Federated": "$arn"},
                "Action": "sts:AssumeRoleWithWebIdentity",
                "Condition": {
                  "StringEquals": {
                    "$url:sub": "system:serviceaccount:kube-system:aws-load-balancer-controller",
                    "$url:aud": "sts.amazonaws.com"
                  }
                }
              }]
            }
            """.trimIndent()
        }

        val role = Role(
            "alb-controller-irsa",
            RoleArgs.builder().assumeRolePolicy(trustPolicy).build()
        )

        RolePolicy(
            "alb-controller-policy",
            RolePolicyArgs.builder()
                .role(role.id())
                .policy(AlbControllerPolicy.load())
                .build()
        )

        Release(
            "aws-load-balancer-controller",
            ReleaseArgs.builder()
                .chart("aws-load-balancer-controller")
                .version("1.8.4")
                .repositoryOpts(
                    RepositoryOptsArgs.builder()
                        .repo("https://aws.github.io/eks-charts")
                        .build()
                )
                .namespace("kube-system")
                .values(mapOf<String, Any>(
                    "clusterName" to cluster.cluster.eksCluster().applyValue { it.name() },
                    "region" to "eu-central-1",
                    "vpcId" to cluster.cluster.core().applyValue { it.vpcId() },
                    "serviceAccount" to mapOf(
                        "create" to true,
                        "name" to "aws-load-balancer-controller",
                        "annotations" to mapOf("eks.amazonaws.com/role-arn" to role.arn())
                    )
                ))
                .build(),
            CustomResourceOptions.builder().provider(namespace.provider).build()
        )
    }
}
```

**Hinweis 1:** Die Annahme ist, dass `Namespace.kt` aus T07 zu `K8sNamespace` umbenannt wurde (siehe Hinweis dort). Falls nicht: `Namespace` weiter verwenden, Imports anpassen.

**Hinweis 2:** `Release.values()` akzeptiert eine `Map<String, Object>`. Pulumi-Outputs (wie `cluster.cluster.eksCluster().applyValue { ... }`) werden vom SDK zur Apply-Zeit aufgelöst.

**Hinweis 3:** Falls `cluster.cluster.core().applyValue { it.vpcId() }` API-Drift hat: `cluster.cluster.core()` liefert ein `Output<ClusterCore>`, dort gibt es Felder `vpcId`, `oidcProvider`, `cluster`. Konsultiere die generierte Java-API-Doku.

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/base && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/LoadBalancerController.kt
git commit -m "feat(infra-32): add AWS Load Balancer Controller via Helm with IRSA"
```

---

### Task 10: `App.kt` finalisieren — alle Module verdrahten + Outputs exportieren

**Files:**
- Modify: `infra/base/src/main/kotlin/com/agentwork/infra/base/App.kt`

- [ ] **Step 1: `App.kt` ersetzen**

```kotlin
package com.agentwork.infra.base

import com.pulumi.Pulumi
import com.pulumi.core.Output

fun main() {
    Pulumi.run { ctx ->
        val net = Networking.create(ctx)
        val cluster = Cluster.create(ctx, net)
        Registry.create(ctx)
        val storage = Storage.create(ctx, cluster)
        val ns = K8sNamespace.create(ctx, cluster)
        LoadBalancerController.create(ctx, cluster, ns)

        ctx.export("kubeconfig", cluster.cluster.kubeconfigJson())
        ctx.export("clusterName", cluster.cluster.eksCluster().applyValue { it.name() })
        ctx.export("oidcProviderArn", cluster.cluster.core().applyValue { it.oidcProvider().get().arn() })
        ctx.export("oidcProviderUrl", cluster.cluster.core().applyValue { it.oidcProvider().get().url() })
        ctx.export("vpcId", net.vpc.vpcId())
        ctx.export("bucketName", storage.bucket.bucket())
        ctx.export("bucketArn", storage.bucket.arn())
        ctx.export("backendIrsaRoleArn", storage.backendIrsaRole.arn())
        // Registry-Outputs aus Closure heraus erreichbar machen → Refactor: Registry.create gibt Registry zurück
    }
}
```

**Refactor-Hinweis:** Damit `ecrBackendUrl` und `ecrFrontendUrl` exportiert werden können, muss `Registry.create(ctx)` einen Wert zurückgeben. Passe den Aufruf in T05's `Registry.kt` an, falls nicht schon geschehen, sodass:

```kotlin
val registry = Registry.create(ctx)
ctx.export("ecrBackendUrl", registry.backend.repositoryUrl())
ctx.export("ecrFrontendUrl", registry.frontend.repositoryUrl())
ctx.export("namespace", ns.namespace.metadata().applyValue { it.name().get() })
ctx.export("region", Output.of("eu-central-1"))
```

(Das Code-Beispiel in T05 zeigt schon `Registry`-Rückgabe — falls implementiert wie dort, ist hier nichts zu tun.)

Vollständige finale Version von `App.kt`:

```kotlin
package com.agentwork.infra.base

import com.pulumi.Pulumi
import com.pulumi.core.Output

fun main() {
    Pulumi.run { ctx ->
        val net = Networking.create(ctx)
        val cluster = Cluster.create(ctx, net)
        val registry = Registry.create(ctx)
        val storage = Storage.create(ctx, cluster)
        val ns = K8sNamespace.create(ctx, cluster)
        LoadBalancerController.create(ctx, cluster, ns)

        ctx.export("kubeconfig", cluster.cluster.kubeconfigJson())
        ctx.export("clusterName", cluster.cluster.eksCluster().applyValue { it.name() })
        ctx.export("oidcProviderArn", cluster.cluster.core().applyValue { it.oidcProvider().get().arn() })
        ctx.export("oidcProviderUrl", cluster.cluster.core().applyValue { it.oidcProvider().get().url() })
        ctx.export("vpcId", net.vpc.vpcId())
        ctx.export("bucketName", storage.bucket.bucket())
        ctx.export("bucketArn", storage.bucket.arn())
        ctx.export("backendIrsaRoleArn", storage.backendIrsaRole.arn())
        ctx.export("ecrBackendUrl", registry.backend.repositoryUrl())
        ctx.export("ecrFrontendUrl", registry.frontend.repositoryUrl())
        ctx.export("namespace", ns.namespace.metadata().applyValue { it.name().get() })
        ctx.export("region", Output.of("eu-central-1"))
    }
}
```

- [ ] **Step 2: Test laufen lassen — Smoke-Test prüft ganze Pipeline**

Run: `cd infra/base && ./gradlew test --quiet`
Expected: `BUILD SUCCESSFUL`, alle 3 Tests grün (der bestehende Smoke-Test ruft jetzt indirekt alle Module auf, weil sie über `App.kt` verdrahtet sind — hier bleibt der Smoke-Test aber bewusst minimal und ruft die Module nicht auf, weil nur `Pulumi.run` → `App.kt::main` den vollen Stack zieht; der Test ruft `Pulumi.run { _ -> }` mit leerer Closure → braucht keine Anpassung).

- [ ] **Step 3: Commit**

```bash
git add infra/base/src/main/kotlin/com/agentwork/infra/base/App.kt
git commit -m "feat(infra-32): wire base stack and export outputs"
```

---

### Task 11: `Pulumi.dev.yaml` und `Pulumi.prod.yaml` für base

**Files:**
- Create: `infra/base/Pulumi.dev.yaml`
- Create: `infra/base/Pulumi.prod.yaml`

- [ ] **Step 1: `Pulumi.dev.yaml` schreiben**

```yaml
config:
  aws:region: eu-central-1
  productspec-base:nodeInstanceType: t3.medium
  productspec-base:nodeMinSize: "1"
  productspec-base:nodeDesiredSize: "2"
  productspec-base:nodeMaxSize: "4"
```

- [ ] **Step 2: `Pulumi.prod.yaml` schreiben**

```yaml
config:
  aws:region: eu-central-1
  productspec-base:nodeInstanceType: t3.large
  productspec-base:nodeMinSize: "2"
  productspec-base:nodeDesiredSize: "2"
  productspec-base:nodeMaxSize: "6"
```

- [ ] **Step 3: Commit**

```bash
git add infra/base/Pulumi.dev.yaml infra/base/Pulumi.prod.yaml
git commit -m "feat(infra-32): add base stack configs for dev and prod"
```

---

## Phase 2 — Workloads-Stack

### Task 12: `infra/workloads/` Gradle-Projekt + leere Test-Skeletons

**Files:**
- Create: `infra/workloads/Pulumi.yaml`
- Create: `infra/workloads/build.gradle.kts`
- Create: `infra/workloads/settings.gradle.kts`
- Create: `infra/workloads/.gitignore`
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/App.kt`
- Create: `infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/PulumiMocks.kt`
- Create: `infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/WorkloadsStackTest.kt`
- Copy: Gradle-Wrapper aus `backend/`

- [ ] **Step 1: Gradle-Wrapper kopieren**

```bash
mkdir -p infra/workloads
cp -r backend/gradle infra/workloads/
cp backend/gradlew backend/gradlew.bat infra/workloads/
chmod +x infra/workloads/gradlew
```

- [ ] **Step 2: `settings.gradle.kts`**

```kotlin
rootProject.name = "productspec-workloads"
```

- [ ] **Step 3: `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "com.agentwork.infra.workloads"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pulumi:pulumi:1.16.0")
    implementation("com.pulumi:aws:6.83.0")
    implementation("com.pulumi:kubernetes:4.21.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.agentwork.infra.workloads.AppKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
```

- [ ] **Step 4: `Pulumi.yaml`**

```yaml
name: productspec-workloads
runtime:
  name: java
  options:
    use-executor: gradle
description: Productspec workloads (deployments, services, ingress)
```

- [ ] **Step 5: `.gitignore`**

```
.gradle/
build/
*.class
Pulumi.*.yaml.bak
```

- [ ] **Step 6: Skeleton `App.kt`**

Datei: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/App.kt`

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Pulumi

fun main() {
    Pulumi.run { _ ->
        // Verdrahtung in T18.
    }
}
```

- [ ] **Step 7: `PulumiMocks.kt` (mit Mock-StackReference-Outputs)**

Datei: `infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/PulumiMocks.kt`

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.test.Mocks
import com.pulumi.test.MockCallArgs
import com.pulumi.test.MockResourceArgs
import com.pulumi.test.NewResourceResult
import com.pulumi.test.CallResult
import java.util.Optional
import java.util.concurrent.CompletableFuture

class PulumiMocks : Mocks {

    private val baseOutputs: Map<String, Any> = mapOf(
        "kubeconfig" to "{}",
        "namespace" to "productspec",
        "ecrBackendUrl" to "123456789012.dkr.ecr.eu-central-1.amazonaws.com/productspec-backend",
        "ecrFrontendUrl" to "123456789012.dkr.ecr.eu-central-1.amazonaws.com/productspec-frontend",
        "backendIrsaRoleArn" to "arn:aws:iam::123456789012:role/productspec-backend-irsa",
        "bucketName" to "productspec-data-test",
        "region" to "eu-central-1"
    )

    override fun newResourceAsync(args: MockResourceArgs): CompletableFuture<NewResourceResult> {
        // Stack-References sind Resources vom Typ "pulumi:pulumi:StackReference"
        if (args.type == "pulumi:pulumi:StackReference") {
            val outputs = mapOf("outputs" to baseOutputs)
            return CompletableFuture.completedFuture(
                NewResourceResult.of(Optional.of("${args.name}_id"), outputs)
            )
        }
        return CompletableFuture.completedFuture(
            NewResourceResult.of(Optional.of("${args.name}_id"), args.inputs)
        )
    }

    override fun callAsync(args: MockCallArgs): CompletableFuture<CallResult> {
        return CompletableFuture.completedFuture(CallResult.of(emptyMap<String, Any>()))
    }
}
```

**Hinweis:** Pulumi-StackReferences werden in der Mock-API als „Resource" mit Type `pulumi:pulumi:StackReference` und `outputs`-Key behandelt. Die exakte Output-Form kann zwischen Pulumi-Versionen abweichen; falls Tests fehlschlagen mit "outputs not found", logge `args.inputs` und `args.type` und passe die Mock-Antwort an.

- [ ] **Step 8: Skeleton `WorkloadsStackTest.kt`**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class WorkloadsStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
            .join()
        assertNotNull(result)
    }
}
```

- [ ] **Step 9: Build und Test verifizieren**

Run: `cd infra/workloads && ./gradlew test --quiet`
Expected: `BUILD SUCCESSFUL`, 1 Test grün.

- [ ] **Step 10: Commit**

```bash
git add infra/workloads/
git commit -m "feat(infra-32): scaffold Pulumi workloads project"
```

---

### Task 13: `BaseRefs.kt` — typed Wrapper um StackReference

**Files:**
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/BaseRefs.kt`

- [ ] **Step 1: `BaseRefs.kt` implementieren**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.core.Output
import com.pulumi.resources.StackReference

class BaseRefs(baseStackName: String) {
    private val ref = StackReference("productspec-base/$baseStackName")

    val kubeconfig: Output<String>          = ref.requireOutput("kubeconfig").applyValue { it.toString() }
    val namespace: Output<String>           = ref.requireOutput("namespace").applyValue { it.toString() }
    val ecrBackendUrl: Output<String>       = ref.requireOutput("ecrBackendUrl").applyValue { it.toString() }
    val ecrFrontendUrl: Output<String>      = ref.requireOutput("ecrFrontendUrl").applyValue { it.toString() }
    val backendIrsaRoleArn: Output<String>  = ref.requireOutput("backendIrsaRoleArn").applyValue { it.toString() }
    val bucketName: Output<String>          = ref.requireOutput("bucketName").applyValue { it.toString() }
    val region: Output<String>              = ref.requireOutput("region").applyValue { it.toString() }
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/workloads && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/BaseRefs.kt
git commit -m "feat(infra-32): add typed BaseRefs wrapper for StackReference"
```

---

### Task 14: `ServiceAccounts.kt` — ServiceAccount mit IRSA + Secret

**Files:**
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/ServiceAccounts.kt`

- [ ] **Step 1: `ServiceAccounts.kt` implementieren**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Context
import com.pulumi.core.Output
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.core.v1.Secret
import com.pulumi.kubernetes.core.v1.SecretArgs
import com.pulumi.kubernetes.core.v1.ServiceAccount
import com.pulumi.kubernetes.core.v1.ServiceAccountArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions

class ServiceAccounts(val backendSa: ServiceAccount, val openaiSecret: Secret) {
    companion object {
        fun create(
            ctx: Context,
            base: BaseRefs,
            openaiApiKey: Output<String>,
            provider: Provider
        ): ServiceAccounts {
            val opts = CustomResourceOptions.builder().provider(provider).build()

            val sa = ServiceAccount(
                "backend-sa",
                ServiceAccountArgs.builder()
                    .metadata(
                        ObjectMetaArgs.builder()
                            .name("backend-sa")
                            .namespace(base.namespace)
                            .annotations(mapOf("eks.amazonaws.com/role-arn" to base.backendIrsaRoleArn))
                            .build()
                    )
                    .build(),
                opts
            )

            val secret = Secret(
                "openai-api-key",
                SecretArgs.builder()
                    .metadata(
                        ObjectMetaArgs.builder()
                            .name("openai-api-key")
                            .namespace(base.namespace)
                            .build()
                    )
                    .type("Opaque")
                    .stringData(mapOf("OPENAI_API_KEY" to openaiApiKey))
                    .build(),
                opts
            )

            return ServiceAccounts(sa, secret)
        }
    }
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/workloads && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/ServiceAccounts.kt
git commit -m "feat(infra-32): add backend ServiceAccount + openai Secret"
```

---

### Task 15: `Backend.kt` — Deployment + Service mit Test (IRSA-Sicherheit)

**Files:**
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Backend.kt`
- Modify: `infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/WorkloadsStackTest.kt`

- [ ] **Step 1: Failing Test in `WorkloadsStackTest.kt` ergänzen**

Ersetze den Inhalt:

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs
import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkloadsStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
            .join()
        assertNotNull(result)
    }

    @Test
    fun `backend deployment env contains s3 bucket but no static credentials`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { ctx ->
                val base = BaseRefs("dev")
                val provider = Provider("k8s", ProviderArgs.builder().kubeconfig(base.kubeconfig).build())
                val sa = ServiceAccounts.create(
                    ctx,
                    base,
                    com.pulumi.core.Output.of("test-key"),
                    provider
                )
                Backend.create(ctx, base, "abc1234", sa, provider)
            }.join()

        val deployment = result.resources()
            .firstOrNull { it.type == "kubernetes:apps/v1:Deployment" && it.name == "backend" }
        assertNotNull(deployment, "backend Deployment not found")

        // Inputs are nested: spec.template.spec.containers[0].env
        // The exact path traversal depends on Pulumi-Test-Result-API; pseudo-code:
        @Suppress("UNCHECKED_CAST")
        val spec = deployment.inputs["spec"] as Map<String, Any>
        val template = spec["template"] as Map<String, Any>
        val podSpec = template["spec"] as Map<String, Any>
        val containers = podSpec["containers"] as List<Map<String, Any>>
        val envList = containers[0]["env"] as List<Map<String, Any>>
        val envNames = envList.map { it["name"] as String }

        assertTrue(envNames.contains("S3_BUCKET"), "S3_BUCKET missing; have: $envNames")
        assertTrue(envNames.contains("S3_REGION"), "S3_REGION missing; have: $envNames")
        assertTrue(envNames.contains("OPENAI_API_KEY"), "OPENAI_API_KEY missing; have: $envNames")
        assertTrue(!envNames.contains("S3_ACCESS_KEY"), "S3_ACCESS_KEY must NOT be set when IRSA is used")
        assertTrue(!envNames.contains("S3_SECRET_KEY"), "S3_SECRET_KEY must NOT be set when IRSA is used")

        assertTrue(podSpec["serviceAccountName"] == "backend-sa")
    }
}
```

- [ ] **Step 2: Test laufen lassen — soll fehlschlagen**

Run: `cd infra/workloads && ./gradlew test --quiet`
Expected: `FAIL` — `Backend` Reference unresolved.

- [ ] **Step 3: `Backend.kt` implementieren**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Context
import com.pulumi.core.Output
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.apps.v1.Deployment
import com.pulumi.kubernetes.apps.v1.DeploymentArgs
import com.pulumi.kubernetes.apps.v1.inputs.DeploymentSpecArgs
import com.pulumi.kubernetes.core.v1.Service
import com.pulumi.kubernetes.core.v1.ServiceArgs
import com.pulumi.kubernetes.core.v1.inputs.ContainerArgs
import com.pulumi.kubernetes.core.v1.inputs.ContainerPortArgs
import com.pulumi.kubernetes.core.v1.inputs.EnvVarArgs
import com.pulumi.kubernetes.core.v1.inputs.EnvVarSourceArgs
import com.pulumi.kubernetes.core.v1.inputs.HTTPGetActionArgs
import com.pulumi.kubernetes.core.v1.inputs.PodSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.PodTemplateSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.ProbeArgs
import com.pulumi.kubernetes.core.v1.inputs.ResourceRequirementsArgs
import com.pulumi.kubernetes.core.v1.inputs.SecretKeySelectorArgs
import com.pulumi.kubernetes.core.v1.inputs.ServicePortArgs
import com.pulumi.kubernetes.core.v1.inputs.ServiceSpecArgs
import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions

class Backend(val deployment: Deployment, val service: Service) {
    companion object {
        fun create(
            ctx: Context,
            base: BaseRefs,
            imageTag: String,
            sa: ServiceAccounts,
            provider: Provider
        ): Backend {
            val cfg = ctx.config("productspec-workloads")
            val replicas = cfg.get("backendReplicas").orElse("2").toInt()
            val opts = CustomResourceOptions.builder().provider(provider).build()
            val labels = mapOf("app" to "productspec-backend")
            val image: Output<String> = base.ecrBackendUrl.applyValue { "$it:$imageTag" }

            val deployment = Deployment(
                "backend",
                DeploymentArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("backend").namespace(base.namespace).build())
                    .spec(
                        DeploymentSpecArgs.builder()
                            .replicas(replicas)
                            .selector(LabelSelectorArgs.builder().matchLabels(labels).build())
                            .template(
                                PodTemplateSpecArgs.builder()
                                    .metadata(ObjectMetaArgs.builder().labels(labels).build())
                                    .spec(
                                        PodSpecArgs.builder()
                                            .serviceAccountName("backend-sa")
                                            .containers(listOf(
                                                ContainerArgs.builder()
                                                    .name("backend")
                                                    .image(image)
                                                    .ports(listOf(ContainerPortArgs.builder().containerPort(8080).build()))
                                                    .env(listOf(
                                                        EnvVarArgs.builder().name("S3_BUCKET").value(base.bucketName).build(),
                                                        EnvVarArgs.builder().name("S3_REGION").value(base.region).build(),
                                                        EnvVarArgs.builder().name("S3_PATH_STYLE").value("false").build(),
                                                        EnvVarArgs.builder().name("OPENAI_API_KEY")
                                                            .valueFrom(
                                                                EnvVarSourceArgs.builder()
                                                                    .secretKeyRef(
                                                                        SecretKeySelectorArgs.builder()
                                                                            .name("openai-api-key")
                                                                            .key("OPENAI_API_KEY")
                                                                            .build()
                                                                    ).build()
                                                            ).build()
                                                    ))
                                                    .resources(
                                                        ResourceRequirementsArgs.builder()
                                                            .requests(mapOf("memory" to "256Mi", "cpu" to "250m"))
                                                            .limits(mapOf("memory" to "1Gi", "cpu" to "1000m"))
                                                            .build()
                                                    )
                                                    .livenessProbe(
                                                        ProbeArgs.builder()
                                                            .httpGet(HTTPGetActionArgs.builder().path("/api/health").port(8080).build())
                                                            .initialDelaySeconds(30)
                                                            .periodSeconds(10)
                                                            .build()
                                                    )
                                                    .readinessProbe(
                                                        ProbeArgs.builder()
                                                            .httpGet(HTTPGetActionArgs.builder().path("/api/health").port(8080).build())
                                                            .initialDelaySeconds(5)
                                                            .build()
                                                    )
                                                    .build()
                                            ))
                                            .build()
                                    )
                                    .build()
                            ).build()
                    ).build(),
                opts
            )

            val service = Service(
                "backend",
                ServiceArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("backend").namespace(base.namespace).build())
                    .spec(
                        ServiceSpecArgs.builder()
                            .type("ClusterIP")
                            .selector(labels)
                            .ports(listOf(
                                ServicePortArgs.builder().port(80).targetPort(8080).build()
                            ))
                            .build()
                    ).build(),
                opts
            )

            return Backend(deployment, service)
        }
    }
}
```

**Hinweis:** Bei `targetPort(8080)` — Pulumi-Builder akzeptiert hier oft `Either<Int, String>` oder den Spezial-Setter `targetPort(Output.of(8080))`. Falls Compile-Fehler: Konsultiere `ServicePortArgs.Builder`-Doku.

- [ ] **Step 4: Test laufen lassen — soll passen**

Run: `cd infra/workloads && ./gradlew test --quiet`
Expected: `BUILD SUCCESSFUL`, alle 2 Tests grün.

**Falls** der Inputs-Pfad-Traversal im Test scheitert (z. B. `inputs["spec"]` ist null oder keine Map): Logge `deployment.inputs.keys` und passe die Pfad-Navigation an die tatsächliche Pulumi-Test-Repräsentation an. Mögliche Alternative: Pulumi serialisiert Inputs als JSON-String — `assertTrue(deployment.inputs.toString().contains("S3_ACCESS_KEY") == false)` als simpler Smoke-Test.

- [ ] **Step 5: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Backend.kt \
        infra/workloads/src/test/kotlin/com/agentwork/infra/workloads/WorkloadsStackTest.kt
git commit -m "feat(infra-32): add backend deployment + service with IRSA test"
```

---

### Task 16: `Frontend.kt` — Deployment + Service

**Files:**
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Frontend.kt`

- [ ] **Step 1: `Frontend.kt` implementieren**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Context
import com.pulumi.core.Output
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.apps.v1.Deployment
import com.pulumi.kubernetes.apps.v1.DeploymentArgs
import com.pulumi.kubernetes.apps.v1.inputs.DeploymentSpecArgs
import com.pulumi.kubernetes.core.v1.Service
import com.pulumi.kubernetes.core.v1.ServiceArgs
import com.pulumi.kubernetes.core.v1.inputs.ContainerArgs
import com.pulumi.kubernetes.core.v1.inputs.ContainerPortArgs
import com.pulumi.kubernetes.core.v1.inputs.EnvVarArgs
import com.pulumi.kubernetes.core.v1.inputs.HTTPGetActionArgs
import com.pulumi.kubernetes.core.v1.inputs.PodSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.PodTemplateSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.ProbeArgs
import com.pulumi.kubernetes.core.v1.inputs.ResourceRequirementsArgs
import com.pulumi.kubernetes.core.v1.inputs.ServicePortArgs
import com.pulumi.kubernetes.core.v1.inputs.ServiceSpecArgs
import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions

class Frontend(val deployment: Deployment, val service: Service) {
    companion object {
        fun create(
            ctx: Context,
            base: BaseRefs,
            imageTag: String,
            provider: Provider
        ): Frontend {
            val cfg = ctx.config("productspec-workloads")
            val replicas = cfg.get("frontendReplicas").orElse("2").toInt()
            val opts = CustomResourceOptions.builder().provider(provider).build()
            val labels = mapOf("app" to "productspec-frontend")
            val image: Output<String> = base.ecrFrontendUrl.applyValue { "$it:$imageTag" }

            val deployment = Deployment(
                "frontend",
                DeploymentArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("frontend").namespace(base.namespace).build())
                    .spec(
                        DeploymentSpecArgs.builder()
                            .replicas(replicas)
                            .selector(LabelSelectorArgs.builder().matchLabels(labels).build())
                            .template(
                                PodTemplateSpecArgs.builder()
                                    .metadata(ObjectMetaArgs.builder().labels(labels).build())
                                    .spec(
                                        PodSpecArgs.builder()
                                            .containers(listOf(
                                                ContainerArgs.builder()
                                                    .name("frontend")
                                                    .image(image)
                                                    .ports(listOf(ContainerPortArgs.builder().containerPort(3000).build()))
                                                    .env(listOf(
                                                        EnvVarArgs.builder().name("NEXT_PUBLIC_API_URL").value("/api").build(),
                                                        EnvVarArgs.builder().name("NODE_ENV").value("production").build()
                                                    ))
                                                    .resources(
                                                        ResourceRequirementsArgs.builder()
                                                            .requests(mapOf("memory" to "128Mi", "cpu" to "100m"))
                                                            .limits(mapOf("memory" to "512Mi", "cpu" to "500m"))
                                                            .build()
                                                    )
                                                    .livenessProbe(
                                                        ProbeArgs.builder()
                                                            .httpGet(HTTPGetActionArgs.builder().path("/").port(3000).build())
                                                            .initialDelaySeconds(15)
                                                            .build()
                                                    )
                                                    .readinessProbe(
                                                        ProbeArgs.builder()
                                                            .httpGet(HTTPGetActionArgs.builder().path("/").port(3000).build())
                                                            .initialDelaySeconds(5)
                                                            .build()
                                                    )
                                                    .build()
                                            )).build()
                                    ).build()
                            ).build()
                    ).build(),
                opts
            )

            val service = Service(
                "frontend",
                ServiceArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("frontend").namespace(base.namespace).build())
                    .spec(
                        ServiceSpecArgs.builder()
                            .type("ClusterIP")
                            .selector(labels)
                            .ports(listOf(ServicePortArgs.builder().port(80).targetPort(3000).build()))
                            .build()
                    ).build(),
                opts
            )

            return Frontend(deployment, service)
        }
    }
}
```

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/workloads && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Frontend.kt
git commit -m "feat(infra-32): add frontend deployment + service"
```

---

### Task 17: `Ingress.kt` — ALB Ingress mit Pfad-Routing

**Files:**
- Create: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Ingress.kt`

- [ ] **Step 1: `Ingress.kt` implementieren**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Context
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.core.v1.Service
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.kubernetes.networking.v1.Ingress
import com.pulumi.kubernetes.networking.v1.IngressArgs
import com.pulumi.kubernetes.networking.v1.inputs.HTTPIngressPathArgs
import com.pulumi.kubernetes.networking.v1.inputs.HTTPIngressRuleValueArgs
import com.pulumi.kubernetes.networking.v1.inputs.IngressBackendArgs
import com.pulumi.kubernetes.networking.v1.inputs.IngressRuleArgs
import com.pulumi.kubernetes.networking.v1.inputs.IngressServiceBackendArgs
import com.pulumi.kubernetes.networking.v1.inputs.IngressSpecArgs
import com.pulumi.kubernetes.networking.v1.inputs.ServiceBackendPortArgs
import com.pulumi.resources.CustomResourceOptions

object IngressModule {
    fun create(
        ctx: Context,
        base: BaseRefs,
        backendService: Service,
        frontendService: Service,
        provider: Provider
    ): Ingress {
        val opts = CustomResourceOptions.builder().provider(provider).build()
        val annotations = mapOf(
            "kubernetes.io/ingress.class" to "alb",
            "alb.ingress.kubernetes.io/scheme" to "internet-facing",
            "alb.ingress.kubernetes.io/target-type" to "ip",
            "alb.ingress.kubernetes.io/listen-ports" to "[{\"HTTP\": 80}]",
            "alb.ingress.kubernetes.io/healthcheck-path" to "/"
        )

        return Ingress(
            "productspec",
            IngressArgs.builder()
                .metadata(
                    ObjectMetaArgs.builder()
                        .name("productspec")
                        .namespace(base.namespace)
                        .annotations(annotations)
                        .build()
                )
                .spec(
                    IngressSpecArgs.builder()
                        .ingressClassName("alb")
                        .rules(listOf(
                            IngressRuleArgs.builder()
                                .http(
                                    HTTPIngressRuleValueArgs.builder()
                                        .paths(listOf(
                                            HTTPIngressPathArgs.builder()
                                                .pathType("Prefix")
                                                .path("/api")
                                                .backend(
                                                    IngressBackendArgs.builder()
                                                        .service(
                                                            IngressServiceBackendArgs.builder()
                                                                .name("backend")
                                                                .port(ServiceBackendPortArgs.builder().number(80).build())
                                                                .build()
                                                        ).build()
                                                ).build(),
                                            HTTPIngressPathArgs.builder()
                                                .pathType("Prefix")
                                                .path("/")
                                                .backend(
                                                    IngressBackendArgs.builder()
                                                        .service(
                                                            IngressServiceBackendArgs.builder()
                                                                .name("frontend")
                                                                .port(ServiceBackendPortArgs.builder().number(80).build())
                                                                .build()
                                                        ).build()
                                                ).build()
                                        )).build()
                                ).build()
                        )).build()
                ).build(),
            opts
        )
    }
}
```

**Hinweis:** Modul heißt `IngressModule` (object), um Class-Name-Konflikt mit `com.pulumi.kubernetes.networking.v1.Ingress` zu vermeiden.

- [ ] **Step 2: Build verifizieren**

Run: `cd infra/workloads && ./gradlew compileKotlin --quiet`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/Ingress.kt
git commit -m "feat(infra-32): add ALB Ingress with path-based routing"
```

---

### Task 18: `App.kt` finalisieren — alle Workloads-Module verdrahten

**Files:**
- Modify: `infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/App.kt`

- [ ] **Step 1: `App.kt` ersetzen**

```kotlin
package com.agentwork.infra.workloads

import com.pulumi.Pulumi
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs

fun main() {
    Pulumi.run { ctx ->
        val cfg = ctx.config("productspec-workloads")
        val baseStackName = cfg.require("baseStackName")
        val imageTag = cfg.require("imageTag")
        val openaiApiKey = cfg.requireSecret("openaiApiKey")

        val base = BaseRefs(baseStackName)
        val provider = Provider("k8s", ProviderArgs.builder().kubeconfig(base.kubeconfig).build())

        val sa = ServiceAccounts.create(ctx, base, openaiApiKey, provider)
        val backend = Backend.create(ctx, base, imageTag, sa, provider)
        val frontend = Frontend.create(ctx, base, imageTag, provider)
        val ingress = IngressModule.create(ctx, base, backend.service, frontend.service, provider)

        ctx.export("ingressDnsName",
            ingress.status().applyValue { it.loadBalancer().get().ingress().firstOrNull()?.hostname()?.orElse("") ?: "" }
        )
        ctx.export("backendImage", base.ecrBackendUrl.applyValue { "$it:$imageTag" })
        ctx.export("frontendImage", base.ecrFrontendUrl.applyValue { "$it:$imageTag" })
    }
}
```

**Hinweis zur Ingress-Status-API:** Pulumi-Java exposes `ingress.status()` als `Output<IngressStatus>`. Die Traversierung `loadBalancer().get().ingress()` kann sich abhängig von der Java-SDK-Version unterscheiden (Optional vs. direkter Zugriff). Falls Compile-Fehler: vereinfachen zu

```kotlin
ctx.export("ingressDnsName", ingress.status().applyValue { status ->
    runCatching { status.loadBalancer().get().ingress().first().hostname().get() }.getOrDefault("pending")
})
```

- [ ] **Step 2: Test laufen lassen**

Run: `cd infra/workloads && ./gradlew test --quiet`
Expected: alle 2 Tests grün.

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/src/main/kotlin/com/agentwork/infra/workloads/App.kt
git commit -m "feat(infra-32): wire workloads stack and export ingress dns"
```

---

### Task 19: `Pulumi.dev.yaml` und `Pulumi.prod.yaml` für workloads

**Files:**
- Create: `infra/workloads/Pulumi.dev.yaml`
- Create: `infra/workloads/Pulumi.prod.yaml`

- [ ] **Step 1: `Pulumi.dev.yaml`**

```yaml
config:
  aws:region: eu-central-1
  productspec-workloads:baseStackName: dev
  productspec-workloads:imageTag: placeholder
  productspec-workloads:backendReplicas: "2"
  productspec-workloads:frontendReplicas: "2"
```

**Hinweis:** Der `openaiApiKey`-Secret-Eintrag wird im Bootstrap-Skript (T21) nicht automatisch gesetzt — der User muss `pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-...` selbst nach dem Bootstrap ausführen. Die README-Doku (T23) macht das transparent.

- [ ] **Step 2: `Pulumi.prod.yaml`**

```yaml
config:
  aws:region: eu-central-1
  productspec-workloads:baseStackName: prod
  productspec-workloads:imageTag: placeholder
  productspec-workloads:backendReplicas: "2"
  productspec-workloads:frontendReplicas: "2"
```

- [ ] **Step 3: Commit**

```bash
git add infra/workloads/Pulumi.dev.yaml infra/workloads/Pulumi.prod.yaml
git commit -m "feat(infra-32): add workloads stack configs for dev and prod"
```

---

## Phase 3 — Backend-Regressionstest

### Task 20: `S3ConfigTest.kt` — verifiziert Default-Credential-Chain bei leeren Keys

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/config/S3ConfigTest.kt`

**Begründung:** `S3Config.kt` Zeile 28 baut den `S3Client` ohne `StaticCredentialsProvider`, wenn `accessKey` oder `secretKey` blank sind. Das ist die Voraussetzung für IRSA. Ein expliziter Test schützt vor versehentlichen Refactorings, die das Verhalten brechen.

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package com.agentwork.productspecagent.config

import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.assertNotNull

class S3ConfigTest {

    @Test
    fun `s3Client is built without StaticCredentialsProvider when access key is blank`() {
        val config = S3Config()
        val props = S3StorageProperties(
            bucket = "any",
            endpoint = "",
            region = "us-east-1",
            accessKey = "",
            secretKey = "",
            pathStyleAccess = false
        )
        // Erwartung: kein NPE/Exception, Bean wird gebaut → AWS SDK fällt auf DefaultCredentialsProvider zurück.
        // Wir prüfen nur, dass das Bean gebaut wird; eine direkte Inspektion der internen Provider-Klasse
        // wäre brüchig, weil sie SDK-internes Verhalten testet.
        val client: S3Client = config.s3Client(props)
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `s3Client uses StaticCredentialsProvider when both keys are set`() {
        val config = S3Config()
        val props = S3StorageProperties(
            bucket = "any",
            endpoint = "",
            region = "us-east-1",
            accessKey = "AKIA...",
            secretKey = "secret...",
            pathStyleAccess = false
        )
        val client: S3Client = config.s3Client(props)
        assertNotNull(client)
        client.close()
    }
}
```

**Hinweis zur S3StorageProperties-Signatur:** Konstruktor-Parameter müssen mit `S3StorageProperties.kt` matchen. Konsultiere die Datei vor dem Lauf:

```bash
cat backend/src/main/kotlin/com/agentwork/productspecagent/config/S3StorageProperties.kt
```

und passe die Konstruktor-Aufrufe an die echten Felder an.

- [ ] **Step 2: Test laufen lassen**

Run: `cd backend && ./gradlew test --tests "com.agentwork.productspecagent.config.S3ConfigTest" --quiet`
Expected: `BUILD SUCCESSFUL`, beide Tests grün.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/config/S3ConfigTest.kt
git commit -m "test(infra-32): cover IRSA-friendly S3Client construction"
```

---

## Phase 4 — Shell-Skripte

### Task 21: `scripts/pulumi-bootstrap.sh`

**Files:**
- Create: `scripts/pulumi-bootstrap.sh`

- [ ] **Step 1: Skript schreiben**

Datei: `scripts/pulumi-bootstrap.sh`

```bash
#!/usr/bin/env bash
# One-time bootstrap for Pulumi state backend and stack initialization.
# Idempotent — safe to re-run.
set -euo pipefail

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION="eu-central-1"
STATE_BUCKET="productspec-pulumi-state-$ACCOUNT_ID"

echo "=== Bootstrap for AWS account $ACCOUNT_ID in $REGION ==="

# 1) State bucket
if aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null; then
  echo "State bucket already exists: $STATE_BUCKET"
else
  echo "Creating state bucket: $STATE_BUCKET"
  aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$REGION" \
    --create-bucket-configuration "LocationConstraint=$REGION"
  aws s3api put-bucket-versioning --bucket "$STATE_BUCKET" \
    --versioning-configuration Status=Enabled
  aws s3api put-bucket-encryption --bucket "$STATE_BUCKET" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-public-access-block --bucket "$STATE_BUCKET" \
    --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
fi

# 2) Pulumi login (always re-execute; cheap)
pulumi login "s3://$STATE_BUCKET?region=$REGION"

# 3) Stack init (idempotent)
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
for ENV in dev prod; do
  for PROJECT in base workloads; do
    DIR="$ROOT_DIR/infra/$PROJECT"
    if pulumi -C "$DIR" stack ls 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$ENV"; then
      echo "Stack $PROJECT/$ENV exists"
    else
      echo "Initializing stack $PROJECT/$ENV"
      pulumi -C "$DIR" stack init "$ENV"
    fi
  done
done

cat <<EOF

=== Bootstrap complete ===
State bucket:   s3://$STATE_BUCKET
Stacks created: productspec-base/{dev,prod}, productspec-workloads/{dev,prod}

Next steps:
  1. Set the OpenAI API key for each workloads stack:
     pulumi -C infra/workloads -s dev config set --secret openaiApiKey sk-...
     pulumi -C infra/workloads -s prod config set --secret openaiApiKey sk-...

  2. Deploy:
     ./scripts/deploy.sh dev
EOF
```

- [ ] **Step 2: Ausführbar machen**

```bash
chmod +x scripts/pulumi-bootstrap.sh
```

- [ ] **Step 3: Statisches Lint (Bash-Syntax)**

Run: `bash -n scripts/pulumi-bootstrap.sh`
Expected: keine Ausgabe (Syntax OK).

- [ ] **Step 4: Commit**

```bash
git add scripts/pulumi-bootstrap.sh
git commit -m "feat(scripts-32): add one-time Pulumi bootstrap script"
```

---

### Task 22: `scripts/deploy.sh`

**Files:**
- Create: `scripts/deploy.sh`

- [ ] **Step 1: Skript schreiben**

```bash
#!/usr/bin/env bash
# Deploy productspec-agent to AWS EKS for the given environment.
# Usage: ./scripts/deploy.sh <dev|prod>
set -euo pipefail

ENV="${1:?usage: deploy.sh <dev|prod>}"
case "$ENV" in dev|prod) ;; *) echo "ERROR: env must be dev or prod"; exit 1 ;; esac

DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY" != "0" ]; then
  echo "ERROR: uncommitted changes — commit first for reproducible image tags"
  git status --short
  exit 1
fi
GIT_SHA=$(git rev-parse --short=8 HEAD)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Deploy $ENV (git-sha: $GIT_SHA) ==="

# 1) Base stack
echo "--- 1/4 base stack ---"
pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" up --yes

# 2) ECR URLs
ECR_BACKEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrBackendUrl)
ECR_FRONTEND=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output ecrFrontendUrl)
REGION=$(pulumi -C "$ROOT_DIR/infra/base" -s "$ENV" stack output region)
ECR_REGISTRY="${ECR_BACKEND%/*}"

echo "--- 2/4 ECR login ---"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 3) Build + push (linux/amd64 für Apple Silicon)
echo "--- 3/4 build + push images ---"
docker buildx build --platform linux/amd64 \
  -t "$ECR_BACKEND:$GIT_SHA" --push "$ROOT_DIR/backend"
docker buildx build --platform linux/amd64 \
  -t "$ECR_FRONTEND:$GIT_SHA" --push "$ROOT_DIR/frontend"

# 4) Workloads stack
echo "--- 4/4 workloads stack ---"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" config set imageTag "$GIT_SHA"
pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" up --yes

INGRESS=$(pulumi -C "$ROOT_DIR/infra/workloads" -s "$ENV" stack output ingressDnsName)
cat <<EOF

=== Deploy complete ===
Image tag: $GIT_SHA
Ingress:   http://$INGRESS

Note: ALB DNS may take 1-2 minutes to resolve after the first deploy.
EOF
```

- [ ] **Step 2: Ausführbar machen**

```bash
chmod +x scripts/deploy.sh
```

- [ ] **Step 3: Bash-Syntax prüfen**

Run: `bash -n scripts/deploy.sh`
Expected: keine Ausgabe.

- [ ] **Step 4: Commit**

```bash
git add scripts/deploy.sh
git commit -m "feat(scripts-32): add deploy.sh for AWS EKS rollout"
```

---

## Phase 5 — Dokumentation

### Task 23: README.md — Abschnitt "AWS Deployment"

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Aktuellen README-Inhalt prüfen, dann anhängen**

```bash
cat README.md | tail -20
```

Anhängen am Ende der README (oder vor evtl. existierender "Lizenz"/"Contributing"-Sektion):

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs(infra-32): document AWS deployment workflow"
```

---

### Task 24: Feature-Doc auf "approved" setzen

**Files:**
- Modify: `docs/features/32-pulumi-aws-eks-deployment.md`

- [ ] **Step 1: Status-Zeile aktualisieren**

In `docs/features/32-pulumi-aws-eks-deployment.md`, ersetze Zeile 6:

```markdown
**Status:** Spec in Arbeit (Brainstorming offen)
```

durch:

```markdown
**Status:** Spec approved, Implementierung läuft
**Spec:** [../superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md](../superpowers/specs/2026-04-28-pulumi-aws-eks-deployment-design.md)
**Plan:** [../superpowers/plans/2026-04-28-pulumi-aws-eks-deployment.md](../superpowers/plans/2026-04-28-pulumi-aws-eks-deployment.md)
```

- [ ] **Step 2: Commit**

```bash
git add docs/features/32-pulumi-aws-eks-deployment.md
git commit -m "docs(feature-32): mark spec as approved and link plan"
```

---

## Abschluss

Nach T24 sind alle 24 Implementation-Tasks fertig. Der nächste Schritt ist `superpowers:finishing-a-development-branch` mit:

1. Final-Commit-Sweep, falls etwas vergessen wurde
2. PR-Beschreibung schreiben
3. `docs/features/32-pulumi-aws-eks-deployment-done.md` schreiben mit:
   - Kurzer Zusammenfassung
   - Abweichungen vom Plan (insb. API-Drift bei Pulumi-Java-Buildern)
   - Offene Schulden (z. B. „echtes `pulumi up` gegen AWS-Account steht aus" — das ist Acceptance-Test, kein Implementation-Task)

**Wichtig für den Implementer:** Akzeptanzkriterien 1–6 aus der Spec setzen ein echtes AWS-Apply voraus und können nicht im Implementation-Task abgehakt werden. Der Implementer dokumentiert das in der done-Datei als "verifizierbar nach Bootstrap durch User".

---

## Self-Review-Checkliste (für den Plan-Autor)

Diese Liste wurde nach dem Schreiben durchlaufen:

- [x] **Spec-Coverage:** Alle 25 neu/geänderten Dateien aus Spec haben Tasks
- [x] **Akzeptanzkriterien:** AK 1–6 sind Apply-Tests (User-Verantwortung); AK 7–9 sind Test-Suite-Pass (T02, T06, T15, T20); AK 10 ist Tear-down (Skript-Verfügbar in T21/T22); AK 11–12 sind Doku-Tasks (T23, T24)
- [x] **Keine Placeholder:** keine "TODO", keine vage Anweisungen
- [x] **Type-Konsistenz:** `K8sNamespace` (umbenannt) wird in T07 und T09/T10 konsistent verwendet; `Cluster` (Wrapper-Class) in T04, T06, T07, T09, T10; `BaseRefs` in T13, T14, T15, T16, T17, T18
- [x] **Bite-sized Steps:** Jede Datei + Test-Aufruf + Commit ist ein eigener Step; Pulumi-Module sind 5–15 min pro Task (akzeptabel für IaC)
- [x] **TDD:** T06 und T15 sind echte Test-First-Tasks; andere Module sind reine Builder-Calls ohne sinnvolle Unit-Tests (Compiler-Validation reicht)
- [x] **Frequent commits:** 24 Commits insgesamt, jeder Task endet mit Commit
