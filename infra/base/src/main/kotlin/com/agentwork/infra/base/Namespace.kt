package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs
import com.pulumi.kubernetes.core.v1.Namespace
import com.pulumi.kubernetes.core.v1.NamespaceArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions

class K8sNamespace(val provider: Provider, val namespace: Namespace) {
    companion object {
        fun create(ctx: Context, cluster: EksCluster): K8sNamespace {
            val provider = Provider(
                "productspec-k8s",
                ProviderArgs.builder().kubeconfig(cluster.cluster.kubeconfigJson()).build()
            )
            val ns = Namespace(
                "productspec-namespace",
                NamespaceArgs.builder()
                    .metadata(ObjectMetaArgs.builder().name("productspec").build())
                    .build(),
                CustomResourceOptions.builder().provider(provider).build()
            )
            return K8sNamespace(provider, ns)
        }
    }
}
