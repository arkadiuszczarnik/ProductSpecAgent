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
