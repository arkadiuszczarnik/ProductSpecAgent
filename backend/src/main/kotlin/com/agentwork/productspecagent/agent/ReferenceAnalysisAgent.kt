package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignInputCategory
import com.agentwork.productspecagent.domain.DesignInputClassification
import org.springframework.stereotype.Component

@Component
open class ReferenceAnalysisAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-reference-analysis"
    }

    open fun analyze(projectId: String): List<DesignInputClassification> =
        listOf(
            DesignInputClassification(
                category = DesignInputCategory.REFERENCE_IMAGE,
                summary = "Reference material is available for design generation.",
                suggestedUse = "Use as visual reference.",
                confidence = 0.4,
            ),
        )
}
