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
