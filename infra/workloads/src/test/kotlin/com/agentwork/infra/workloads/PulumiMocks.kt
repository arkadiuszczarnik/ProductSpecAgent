package com.agentwork.infra.workloads

import com.pulumi.test.Mocks
import java.util.Optional
import java.util.concurrent.CompletableFuture

class PulumiMocks(
    private val recorded: MutableList<Mocks.ResourceArgs> = mutableListOf()
) : Mocks {
    val resources: List<Mocks.ResourceArgs> get() = recorded.toList()

    private val baseOutputs: Map<String, Any> = mapOf(
        "kubeconfig" to "{}",
        "namespace" to "productspec",
        "ecrBackendUrl" to "123456789012.dkr.ecr.eu-central-1.amazonaws.com/productspec-backend",
        "ecrFrontendUrl" to "123456789012.dkr.ecr.eu-central-1.amazonaws.com/productspec-frontend",
        "backendIrsaRoleArn" to "arn:aws:iam::123456789012:role/productspec-backend-irsa",
        "bucketName" to "productspec-data-test",
        "region" to "eu-central-1"
    )

    override fun newResourceAsync(args: Mocks.ResourceArgs): CompletableFuture<Mocks.ResourceResult> {
        recorded.add(args)
        if (args.type == "pulumi:pulumi:StackReference") {
            val outputs = mapOf("outputs" to baseOutputs)
            return CompletableFuture.completedFuture(
                Mocks.ResourceResult.of(Optional.of("${args.name}_id"), outputs)
            )
        }
        return CompletableFuture.completedFuture(
            Mocks.ResourceResult.of(Optional.of("${args.name}_id"), args.inputs ?: emptyMap<String, Any>())
        )
    }

    override fun callAsync(args: Mocks.CallArgs): CompletableFuture<Map<String, Any>> {
        return CompletableFuture.completedFuture(emptyMap())
    }
}
