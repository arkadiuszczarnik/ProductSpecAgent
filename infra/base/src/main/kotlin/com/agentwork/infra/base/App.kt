package com.agentwork.infra.base

import com.pulumi.Pulumi
import com.pulumi.core.Output

fun main() {
    Pulumi.run { ctx ->
        val net = Networking.create(ctx)
        val cluster = EksCluster.create(ctx, net)
        val registry = Registry.create(ctx)
        val storage = Storage.create(ctx, cluster)
        val ns = K8sNamespace.create(ctx, cluster)
        LoadBalancerController.create(ctx, cluster, ns)

        ctx.export("kubeconfig", cluster.cluster.kubeconfigJson())
        ctx.export("clusterName", cluster.cluster.eksCluster().applyValue { it.name() })
        ctx.export("oidcProviderArn", cluster.cluster.oidcProviderArn())
        ctx.export("oidcProviderUrl", cluster.cluster.oidcProviderUrl())
        ctx.export("vpcId", net.vpc.vpcId())
        ctx.export("bucketName", storage.bucket.bucket())
        ctx.export("bucketArn", storage.bucket.arn())
        ctx.export("backendIrsaRoleArn", storage.backendIrsaRole.arn())
        ctx.export("ecrBackendUrl", registry.backend.repositoryUrl())
        ctx.export("ecrFrontendUrl", registry.frontend.repositoryUrl())
        ctx.export("namespace", ns.namespace.metadata().applyValue { it.name().get() })
        ctx.export("region", Output.of("eu-central-1"))
        ctx.export("natInstanceId", net.natInstance.id())
        ctx.export("natPublicIp", net.natEip.publicIp())
    }
}
