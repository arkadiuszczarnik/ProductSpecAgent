package com.agentwork.infra.base

import com.pulumi.test.Mocks
import java.util.Optional
import java.util.concurrent.CompletableFuture

class PulumiMocks(private val recorded: MutableList<Mocks.ResourceArgs> = mutableListOf()) : Mocks {

    val resources: List<Mocks.ResourceArgs> get() = recorded.toList()

    override fun newResourceAsync(args: Mocks.ResourceArgs): CompletableFuture<Mocks.ResourceResult> {
        recorded.add(args)
        // Synthetische ID: <name>_id; Inputs werden 1:1 als Outputs zurückgegeben.
        // Spezialfälle für Resources, die berechnete Ausgaben brauchen:
        val outputs = HashMap<String, Any>(args.inputs)
        when (args.type) {
            // awsx VPC: mock outputs that Pulumi expects as Lists (not Maps from input serialization)
            "awsx:ec2:Vpc" -> {
                outputs["natGateways"] = emptyList<Any>()
                outputs["eips"] = emptyList<Any>()
                outputs["publicSubnetIds"] = listOf("subnet-public-0", "subnet-public-1", "subnet-public-2")
                outputs["privateSubnetIds"] = listOf("subnet-private-0", "subnet-private-1", "subnet-private-2")
                outputs["isolatedSubnetIds"] = emptyList<Any>()
                outputs["routeTableAssociations"] = emptyList<Any>()
                outputs["routeTables"] = emptyList<Any>()
                outputs["routes"] = emptyList<Any>()
                outputs["subnets"] = emptyList<Any>()
                outputs["vpcEndpoints"] = emptyList<Any>()
                outputs["subnetLayout"] = emptyList<Any>()
                outputs["vpcId"] = "vpc-mock-id"
            }
            "aws:s3/bucket:Bucket" -> outputs["arn"] = "arn:aws:s3:::${args.name}"
            "aws:iam/role:Role" -> outputs["arn"] = "arn:aws:iam::123456789012:role/${args.name}"
            "aws:ecr/repository:Repository" -> {
                outputs["repositoryUrl"] = "123456789012.dkr.ecr.eu-central-1.amazonaws.com/${args.name}"
                outputs["arn"] = "arn:aws:ecr:eu-central-1:123456789012:repository/${args.name}"
            }
            "eks:index:Cluster" -> {
                outputs["kubeconfig"] = "{}"
                outputs["oidcProviderArn"] = "arn:aws:iam::123456789012:oidc-provider/oidc.eks.eu-central-1.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE"
                outputs["oidcProviderUrl"] = "oidc.eks.eu-central-1.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE"
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
