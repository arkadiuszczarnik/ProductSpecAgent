package com.agentwork.productspecagent.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "feature-proposal.uploads")
data class FeatureProposalUploadsProperties(
    @field:Positive
    val maxBytesPerFile: Long = 102_400,
    @field:Positive
    val maxBytesTotal: Long = 512_000,
)
