package com.agentwork.infra.workloads

import com.pulumi.core.Output
import com.pulumi.resources.StackReference

class BaseRefs(baseStackName: String) {
    // S3-State-Backend verlangt das Literal "organization/" als Org-Segment;
    // bei Pulumi Cloud wäre stattdessen die echte Org einzutragen.
    private val ref = StackReference("organization/productspec-base/$baseStackName")

    val kubeconfig: Output<String>          = ref.requireOutput("kubeconfig").applyValue { it.toString() }
    val namespace: Output<String>           = ref.requireOutput("namespace").applyValue { it.toString() }
    val ecrBackendUrl: Output<String>       = ref.requireOutput("ecrBackendUrl").applyValue { it.toString() }
    val ecrFrontendUrl: Output<String>      = ref.requireOutput("ecrFrontendUrl").applyValue { it.toString() }
    val backendIrsaRoleArn: Output<String>  = ref.requireOutput("backendIrsaRoleArn").applyValue { it.toString() }
    val bucketName: Output<String>          = ref.requireOutput("bucketName").applyValue { it.toString() }
    val region: Output<String>              = ref.requireOutput("region").applyValue { it.toString() }
}
