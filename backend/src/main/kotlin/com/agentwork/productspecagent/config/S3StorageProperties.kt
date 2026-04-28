package com.agentwork.productspecagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.storage")
data class S3StorageProperties(
    val bucket: String,
    val endpoint: String = "",
    val region: String = "us-east-1",
    val accessKey: String = "",
    val secretKey: String = "",
    val pathStyleAccess: Boolean = false,
)
