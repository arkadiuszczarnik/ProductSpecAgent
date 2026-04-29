package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.cloudwatch.LogGroup
import com.pulumi.aws.cloudwatch.LogGroupArgs
import com.pulumi.aws.eks.inputs.NodeGroupScalingConfigArgs
import com.pulumi.aws.iam.Role
import com.pulumi.aws.iam.RoleArgs
import com.pulumi.aws.iam.RolePolicyAttachment
import com.pulumi.aws.iam.RolePolicyAttachmentArgs
import com.pulumi.eks.Cluster
import com.pulumi.eks.ClusterArgs
import com.pulumi.eks.ManagedNodeGroup
import com.pulumi.eks.ManagedNodeGroupArgs
import com.pulumi.eks.enums.AuthenticationMode

class EksCluster(
    val cluster: Cluster,
    val nodeRole: Role,
    val nodeGroup: ManagedNodeGroup
) {
    companion object {
        fun create(ctx: Context, networking: Networking): EksCluster {
            val cfg = ctx.config("productspec-base")
            val instanceType = cfg.get("nodeInstanceType").orElse("t3.medium")
            val minSize = cfg.get("nodeMinSize").orElse("1").toInt()
            val desiredSize = cfg.get("nodeDesiredSize").orElse("2").toInt()
            val maxSize = cfg.get("nodeMaxSize").orElse("4").toInt()
            val capacityType = cfg.get("nodeCapacityType").orElse("SPOT")
            val diskSize = cfg.get("nodeDiskSize").orElse("10").toInt()
            val logRetentionDays = cfg.get("logRetentionDays").orElse("7").toInt()

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

            val logGroup = LogGroup(
                "productspec-eks-logs",
                LogGroupArgs.builder()
                    .name("/aws/eks/productspec-eks/cluster")
                    .retentionInDays(logRetentionDays)
                    .build()
            )

            val cluster = Cluster(
                "productspec-eks",
                ClusterArgs.builder()
                    .vpcId(networking.vpc.vpcId())
                    .publicSubnetIds(networking.vpc.publicSubnetIds())
                    .privateSubnetIds(networking.vpc.privateSubnetIds())
                    .skipDefaultNodeGroup(true)
                    .authenticationMode(AuthenticationMode.Api)
                    .createOidcProvider(true)
                    .enabledClusterLogTypes(listOf("api", "audit", "authenticator"))
                    .build(),
                com.pulumi.resources.ComponentResourceOptions.builder()
                    .dependsOn(logGroup)
                    .build()
            )

            // scalingConfig: com.pulumi.aws.eks.inputs.NodeGroupScalingConfigArgs (aus AWS-Provider, nicht EKS-Provider)
            val nodeGroup = ManagedNodeGroup(
                "productspec-nodes",
                ManagedNodeGroupArgs.builder()
                    .cluster(cluster)
                    .nodeRole(nodeRole)
                    .instanceTypes(instanceType)
                    // AL2023_ARM_64_STANDARD passt zu t4g/Graviton-Instances. Falls instanceType
                    // auf x86 geändert wird (z. B. t3.*), muss amiType auf AL2023_x86_64_STANDARD.
                    .amiType("AL2023_ARM_64_STANDARD")
                    .capacityType(capacityType)
                    .diskSize(diskSize)
                    .scalingConfig(
                        NodeGroupScalingConfigArgs.builder()
                            .minSize(minSize)
                            .desiredSize(desiredSize)
                            .maxSize(maxSize)
                            .build()
                    )
                    .build()
            )

            return EksCluster(cluster, nodeRole, nodeGroup)
        }
    }
}
