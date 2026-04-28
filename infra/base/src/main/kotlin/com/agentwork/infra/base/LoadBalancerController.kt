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
    // Helm-Chart 1.8.3 entspricht Controller v2.8.3 (siehe T08).
    private const val CHART_VERSION = "1.8.3"

    fun create(ctx: Context, cluster: EksCluster, namespace: K8sNamespace) {
        // OIDC-Daten direkt aus Cluster (kein Optional-Wrapper, siehe T06)
        val oidcArn: Output<String> = cluster.cluster.oidcProviderArn()
        val oidcUrl: Output<String> = cluster.cluster.oidcProviderUrl()

        val trustPolicy: Output<String> = Output.tuple(oidcArn, oidcUrl).applyValue { tup ->
            val arn = tup.t1
            val url = tup.t2
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

        // Helm-Release. Values als Map<String, Object>; Pulumi-Outputs werden zur Apply-Zeit aufgelöst.
        // Annotation für IRSA: ServiceAccount-Annotation eks.amazonaws.com/role-arn = role.arn()
        Release(
            "aws-load-balancer-controller",
            ReleaseArgs.builder()
                .chart("aws-load-balancer-controller")
                .version(CHART_VERSION)
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
