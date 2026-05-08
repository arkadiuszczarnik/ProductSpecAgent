package com.agentwork.productspecagent.domain

/**
 * Internal product category representation. API DTOs should expose wireValue strings at boundaries.
 */
enum class ProductCategory(val wireValue: String) {
    SAAS("SaaS"),
    MOBILE_APP("Mobile App"),
    CLI_TOOL("CLI Tool"),
    LIBRARY("Library"),
    DESKTOP_APP("Desktop App"),
    API("API");

    companion object {
        fun fromWire(value: String?): ProductCategory? =
            entries.firstOrNull { it.wireValue == value }
    }
}
