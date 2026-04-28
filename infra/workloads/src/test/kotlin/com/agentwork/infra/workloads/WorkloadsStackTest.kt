package com.agentwork.infra.workloads

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class WorkloadsStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ -> }
        assertNotNull(result)
    }
}
