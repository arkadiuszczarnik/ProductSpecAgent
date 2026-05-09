package com.agentwork.productspecagent.agent

import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
data class ScreenProposal(
    val id: String,
    val name: String,
    val purpose: String,
)

@Component
open class ScreenProposalAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-screen-proposal"
    }

    open fun propose(projectId: String): List<ScreenProposal> =
        listOf(
            ScreenProposal(id = "landing", name = "Landing", purpose = "Explain product value and first action."),
            ScreenProposal(id = "dashboard", name = "Dashboard", purpose = "Show the primary logged-in workspace."),
        )
}
