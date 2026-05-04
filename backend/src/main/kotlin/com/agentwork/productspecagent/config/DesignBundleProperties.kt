package com.agentwork.productspecagent.config

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("design.bundle")
data class DesignBundleProperties(
    @field:Positive val maxZipBytes: Long = 5L * 1024 * 1024,
    @field:Positive val maxExtractedBytes: Long = 10L * 1024 * 1024,
    @field:Positive val maxFiles: Int = 500,
    @field:Positive val maxFileBytes: Long = 5L * 1024 * 1024,
    @field:Positive val summaryMaxFileBytes: Long = 50L * 1024,
    @field:Positive val summaryMaxTotalBytes: Long = 200L * 1024,
    @field:Positive val summaryMaxJsxFiles: Int = 5,
)
