package com.agentwork.infra.base

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaseStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
        assertNotNull(result)
    }

    @Test
    fun `s3 bucket public access block has all four toggles true`() {
        val mocks = PulumiMocks()
        PulumiTest.withMocks(mocks)
            .runTest { ctx ->
                val net = Networking.create(ctx)
                val cluster = EksCluster.create(ctx, net)
                Storage.create(ctx, cluster)
            }

        val pab = mocks.resources.firstOrNull {
            it.type == "aws:s3/bucketPublicAccessBlock:BucketPublicAccessBlock"
        }
        assertNotNull(pab, "BucketPublicAccessBlock not found in recorded resources")
        val inputs = pab.inputs ?: error("inputs map is null")
        assertEquals(true, inputs["blockPublicAcls"], "blockPublicAcls must be true")
        assertEquals(true, inputs["ignorePublicAcls"], "ignorePublicAcls must be true")
        assertEquals(true, inputs["blockPublicPolicy"], "blockPublicPolicy must be true")
        assertEquals(true, inputs["restrictPublicBuckets"], "restrictPublicBuckets must be true")
    }

    @Test
    fun `nat instance has source dest check disabled`() {
        val mocks = PulumiMocks()
        PulumiTest.withMocks(mocks).runTest { ctx ->
            Networking.create(ctx)
        }

        val nat = mocks.resources.firstOrNull { it.type == "aws:ec2/instance:Instance" && it.name == "productspec-nat" }
        requireNotNull(nat) { "NAT instance not found in stack" }
        val sourceDestCheck = (nat.inputs ?: error("inputs missing"))["sourceDestCheck"]
        assertEquals(false, sourceDestCheck, "NAT instance MUST have sourceDestCheck=false to forward packets; was: $sourceDestCheck")
    }

    @Test
    fun `irsa role trust policy targets backend-sa in productspec namespace`() {
        val mocks = PulumiMocks()
        PulumiTest.withMocks(mocks)
            .runTest { ctx ->
                val net = Networking.create(ctx)
                val cluster = EksCluster.create(ctx, net)
                Storage.create(ctx, cluster)
            }

        val irsa = mocks.resources.firstOrNull {
            it.type == "aws:iam/role:Role" && it.name == "productspec-backend-irsa"
        }
        assertNotNull(irsa, "backend-irsa Role not found in recorded resources")
        val irsaInputs = irsa.inputs ?: error("irsa inputs map is null")
        val trustPolicy = irsaInputs["assumeRolePolicy"] as String
        assertTrue(
            trustPolicy.contains("system:serviceaccount:productspec:backend-sa"),
            "trust policy must reference backend-sa subject; was: $trustPolicy"
        )
    }
}
