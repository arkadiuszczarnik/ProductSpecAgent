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
                                                        // Solange der ALB nur HTTP serviert (kein TLS-Cert/Domain), darf der
                                                        // Session-Cookie nicht "Secure" sein – sonst sendet der Browser ihn
                                                        // nie zurück und Login schlägt fehl. Auf prod mit HTTPS auf "true" setzen.
                                                        EnvVarArgs.builder().name("AUTH_COOKIE_SECURE").value("false").build(),
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
