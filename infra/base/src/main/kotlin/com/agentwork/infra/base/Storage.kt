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
        fun create(ctx: Context, cluster: EksCluster): Storage {
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

            val oidcArn = cluster.cluster.oidcProviderArn()
            val oidcUrl = cluster.cluster.oidcProviderUrl()

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
