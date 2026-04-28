package com.agentwork.infra.base

import com.pulumi.Context
import com.pulumi.awsx.ec2.Vpc
import com.pulumi.awsx.ec2.VpcArgs

class Networking(val vpc: Vpc) {
    companion object {
        fun create(ctx: Context): Networking {
            val vpc = Vpc(
                "productspec-vpc",
                VpcArgs.builder()
                    .cidrBlock("10.0.0.0/16")
                    .numberOfAvailabilityZones(3)
                    .enableDnsHostnames(true)
                    .build()
            )
            return Networking(vpc)
        }
    }
}
