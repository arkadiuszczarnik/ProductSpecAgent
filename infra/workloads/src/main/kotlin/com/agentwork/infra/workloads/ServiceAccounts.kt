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

            val irsaAnnotations: Output<Map<String, String>> = base.backendIrsaRoleArn.applyValue { arn ->
                mapOf("eks.amazonaws.com/role-arn" to arn)
            }

            val sa = ServiceAccount(
                "backend-sa",
                ServiceAccountArgs.builder()
                    .metadata(
                        ObjectMetaArgs.builder()
                            .name("backend-sa")
                            .namespace(base.namespace)
                            .annotations(irsaAnnotations)
                            .build()
                    )
                    .build(),
                opts
            )

            val openaiSecretData: Output<Map<String, String>> = openaiApiKey.applyValue { key ->
                mapOf("OPENAI_API_KEY" to key)
            }

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
                    .stringData(openaiSecretData)
                    .build(),
                opts
            )

            return ServiceAccounts(sa, secret)
        }
    }
}
