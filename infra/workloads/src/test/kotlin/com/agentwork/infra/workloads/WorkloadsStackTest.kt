package com.agentwork.infra.workloads

import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs
import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkloadsStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
        assertNotNull(result)
    }

    @Test
    fun `backend deployment env contains s3 bucket but no static credentials`() {
        // Recorder-Pattern: TestResult.resources() hat keine inputs() in Pulumi 1.13.2.
        val mocks = PulumiMocks()
        PulumiTest.withMocks(mocks).runTest { ctx ->
            val base = BaseRefs("dev")
            val provider = Provider("k8s", ProviderArgs.builder().kubeconfig(base.kubeconfig).build())
            val sa = ServiceAccounts.create(
                ctx,
                base,
                com.pulumi.core.Output.of("test-key"),
                provider
            )
            Backend.create(ctx, base, "abc1234", sa, provider)
        }

        val deployment = mocks.resources.firstOrNull {
            it.type == "kubernetes:apps/v1:Deployment" && it.name == "backend"
        } ?: error("backend Deployment not found in recorded resources")

        @Suppress("UNCHECKED_CAST")
        val inputs = deployment.inputs ?: error("inputs missing")
        @Suppress("UNCHECKED_CAST") val spec = inputs["spec"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val template = spec["template"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val podSpec = template["spec"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST") val containers = podSpec["containers"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST") val envList = containers[0]["env"] as List<Map<String, Any>>
        val envNames = envList.map { it["name"] as String }

        assertTrue(envNames.contains("S3_BUCKET"), "S3_BUCKET missing; have: $envNames")
        assertTrue(envNames.contains("S3_REGION"), "S3_REGION missing; have: $envNames")
        assertTrue(envNames.contains("OPENAI_API_KEY"), "OPENAI_API_KEY missing; have: $envNames")
        assertTrue(!envNames.contains("S3_ACCESS_KEY"), "S3_ACCESS_KEY must NOT be set when IRSA is used")
        assertTrue(!envNames.contains("S3_SECRET_KEY"), "S3_SECRET_KEY must NOT be set when IRSA is used")

        assertTrue(podSpec["serviceAccountName"] == "backend-sa")
    }
}
