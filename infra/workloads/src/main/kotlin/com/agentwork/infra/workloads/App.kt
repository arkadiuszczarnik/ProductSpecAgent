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
            ingress.status().applyValue { optStatus ->
                runCatching {
                    optStatus.orElse(null)
                        ?.loadBalancer()?.orElse(null)
                        ?.ingress()?.firstOrNull()
                        ?.hostname()?.orElse(null)
                        ?: "pending"
                }.getOrDefault("pending")
            }
        )
        ctx.export("backendImage", base.ecrBackendUrl.applyValue { "$it:$imageTag" })
        ctx.export("frontendImage", base.ecrFrontendUrl.applyValue { "$it:$imageTag" })
    }
}
