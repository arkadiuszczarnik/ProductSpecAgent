package com.agentwork.infra.base

import com.pulumi.test.Mocks
import java.util.Optional
import java.util.concurrent.CompletableFuture

class PulumiMocks : Mocks {
    override fun newResourceAsync(args: Mocks.ResourceArgs): CompletableFuture<Mocks.ResourceResult> {
        // Synthetische ID: <name>_id; Inputs werden 1:1 als Outputs zurückgegeben.
        // Spezialfälle für Resources, die berechnete Ausgaben brauchen:
        val outputs = HashMap<String, Any>(args.inputs)
        when (args.type) {
            "aws:s3/bucket:Bucket" -> outputs["arn"] = "arn:aws:s3:::${args.name}"
            "aws:iam/role:Role" -> outputs["arn"] = "arn:aws:iam::123456789012:role/${args.name}"
            "aws:ecr/repository:Repository" -> {
                outputs["repositoryUrl"] = "123456789012.dkr.ecr.eu-central-1.amazonaws.com/${args.name}"
                outputs["arn"] = "arn:aws:ecr:eu-central-1:123456789012:repository/${args.name}"
            }
            "eks:index:Cluster" -> {
                outputs["kubeconfig"] = "{}"
            }
        }
        return CompletableFuture.completedFuture(
            Mocks.ResourceResult.of(Optional.of("${args.name}_id"), outputs)
        )
    }

    override fun callAsync(args: Mocks.CallArgs): CompletableFuture<Map<String, Any>> {
        return CompletableFuture.completedFuture(emptyMap())
    }
}
