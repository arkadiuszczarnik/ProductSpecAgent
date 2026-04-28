package com.agentwork.infra.base

import com.pulumi.test.PulumiTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class BaseStackTest {

    @Test
    fun `stack runs without errors with mocks`() {
        val result = PulumiTest.withMocks(PulumiMocks())
            .runTest { _ ->
                // Wird in T03-T10 schrittweise gefüllt.
            }
        assertNotNull(result)
    }
}
