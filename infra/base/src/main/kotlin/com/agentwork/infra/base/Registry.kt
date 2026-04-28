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
