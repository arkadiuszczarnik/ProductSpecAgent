package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,        // lowercase, eindeutig
    val passwordHash: String, // BCrypt, Strength 10
    val createdAt: String,    // ISO-8601 UTC
)
