package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.aws.ec2.Ec2Functions
import com.pulumi.aws.ec2.Eip
import com.pulumi.aws.ec2.EipArgs
import com.pulumi.aws.ec2.Instance
import com.pulumi.aws.ec2.InstanceArgs
import com.pulumi.aws.ec2.Route
import com.pulumi.aws.ec2.RouteArgs
import com.pulumi.aws.ec2.SecurityGroup
import com.pulumi.aws.ec2.SecurityGroupArgs
import com.pulumi.aws.ec2.Tag
import com.pulumi.aws.ec2.TagArgs
import com.pulumi.aws.ec2.inputs.GetAmiArgs
import com.pulumi.aws.ec2.inputs.GetAmiFilterArgs
import com.pulumi.aws.ec2.inputs.SecurityGroupEgressArgs
import com.pulumi.aws.ec2.inputs.SecurityGroupIngressArgs
import com.pulumi.awsx.ec2.Vpc
import com.pulumi.awsx.ec2.VpcArgs
import com.pulumi.awsx.ec2.enums.NatGatewayStrategy
import com.pulumi.awsx.ec2.inputs.NatGatewayConfigurationArgs

class Networking(
    val vpc: Vpc,
    val natInstance: Instance,
    val natEip: Eip,
) {
    companion object {
        fun create(ctx: Context): Networking {
            val vpc = Vpc(
                "productspec-vpc",
                VpcArgs.builder()
                    .cidrBlock("10.0.0.0/16")
                    .numberOfAvailabilityZones(3)
                    .enableDnsHostnames(true)
                    .natGateways(
                        NatGatewayConfigurationArgs.builder()
                            .strategy(NatGatewayStrategy.None)
                            .build()
                    )
                    .build()
            )

            // fck-nat AMI lookup: arm64, owned by 568608671756
            val fckNatAmi = Ec2Functions.getAmi(
                GetAmiArgs.builder()
                    .mostRecent(true)
                    .owners("568608671756")
                    .filters(
                        GetAmiFilterArgs.builder()
                            .name("name")
                            .values("fck-nat-al2023-*")
                            .build(),
                        GetAmiFilterArgs.builder()
                            .name("architecture")
                            .values("arm64")
                            .build()
                    )
                    .build()
            )
            val amiId = fckNatAmi.applyValue { it.id() }

            // Security group: allow all traffic from VPC CIDR, all egress
            val natSg = SecurityGroup(
                "productspec-nat-sg",
                SecurityGroupArgs.builder()
                    .vpcId(vpc.vpcId())
                    .description("NAT instance security group")
                    .ingress(
                        SecurityGroupIngressArgs.builder()
                            .protocol("-1")
                            .fromPort(0)
                            .toPort(0)
                            .cidrBlocks("10.0.0.0/16")
                            .build()
                    )
                    .egress(
                        SecurityGroupEgressArgs.builder()
                            .protocol("-1")
                            .fromPort(0)
                            .toPort(0)
                            .cidrBlocks("0.0.0.0/0")
                            .build()
                    )
                    .build()
            )

            // NAT Instance in first public subnet
            val firstPublicSubnetId = vpc.publicSubnetIds().applyValue { ids -> ids.first() }

            val natInstance = Instance(
                "productspec-nat",
                InstanceArgs.builder()
                    .ami(amiId)
                    .instanceType("t4g.nano")
                    .subnetId(firstPublicSubnetId)
                    .vpcSecurityGroupIds(natSg.id().applyValue { listOf(it) })
                    .sourceDestCheck(false)
                    .associatePublicIpAddress(true)
                    .tags(mapOf("Name" to "productspec-nat"))
                    .build()
            )

            val natEip = Eip(
                "productspec-nat-eip",
                EipArgs.builder()
                    .instance(natInstance.id())
                    .domain("vpc")
                    .build()
            )

            // Add default route via NAT instance ENI in every private subnet's route table.
            // vpc.routeTableAssociations() contains associations for all subnets (public + private).
            // We match by subnetId: for each association whose subnetId is in the private-subnet list,
            // we create a default route pointing at the NAT instance's primary ENI.
            val natEniId = natInstance.primaryNetworkInterfaceId()
            vpc.routeTableAssociations().applyValue { assocList ->
                vpc.privateSubnetIds().applyValue { privateIds ->
                    val privateIdSet = privateIds.toSet()
                    assocList.forEachIndexed { index, assoc ->
                        assoc.subnetId().applyValue { optSubnetId ->
                            val subnetId = optSubnetId.orElse("")
                            if (subnetId in privateIdSet) {
                                Route(
                                    "nat-route-private-$index",
                                    RouteArgs.builder()
                                        .routeTableId(assoc.routeTableId())
                                        .destinationCidrBlock("0.0.0.0/0")
                                        .networkInterfaceId(natEniId)
                                        .build()
                                )
                            }
                        }
                    }
                }
            }

            // Subnet-Tags für aws-load-balancer-controller-Auto-Discovery.
            // Public-Subnets -> kubernetes.io/role/elb=1 (für internet-facing ALB)
            // Private-Subnets -> kubernetes.io/role/internal-elb=1 (für internal ALB)
            // Ohne diese Tags scheitert jeder Ingress mit "couldn't auto-discover subnets".
            vpc.publicSubnetIds().applyValue { ids ->
                ids.forEachIndexed { idx, subnetId ->
                    Tag(
                        "subnet-elb-public-$idx",
                        TagArgs.builder()
                            .resourceId(subnetId)
                            .key("kubernetes.io/role/elb")
                            .value("1")
                            .build()
                    )
                }
            }
            vpc.privateSubnetIds().applyValue { ids ->
                ids.forEachIndexed { idx, subnetId ->
                    Tag(
                        "subnet-elb-private-$idx",
                        TagArgs.builder()
                            .resourceId(subnetId)
                            .key("kubernetes.io/role/internal-elb")
                            .value("1")
                            .build()
                    )
                }
            }

            return Networking(vpc, natInstance, natEip)
        }
    }
}
