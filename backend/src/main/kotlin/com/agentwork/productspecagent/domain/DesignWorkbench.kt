package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class DesignWorkbench(
    val projectId: String,
    val description: String? = null,
    val imageInput: DesignImageInput? = null,
    val imageAnalysis: DesignImageAnalysis? = null,
    val imageAnalysisError: String? = null,
    val analysis: DesignAnalysis? = null,
    val currentDesign: GeneratedDesign? = null,
    val updatedAt: String,
)

@Serializable
data class DesignImageInput(
    val originalName: String,
    val contentRef: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: String,
)

@Serializable
data class DesignImageAnalysis(
    val summary: String,
    val palette: List<DesignColor>,
    val typography: List<DesignTypographySignal>,
    val layoutHierarchy: List<DesignLayoutRegion>,
    val components: List<DesignComponentSignal>,
    val moodTags: List<String>,
    val brandSignals: List<String>,
    val designBrief: String,
)

@Serializable
data class DesignColor(
    val hex: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignTypographySignal(
    val category: String,
    val role: String,
    val weight: String,
    val notes: String,
)

@Serializable
data class DesignLayoutRegion(
    val name: String,
    val order: Int,
    val priority: Int,
    val description: String,
)

@Serializable
data class DesignComponentSignal(
    val name: String,
    val role: String,
    val description: String,
)

@Serializable
data class DesignAnalysis(
    val summary: String,
    val visualDirection: String,
    val rationale: String,
)

@Serializable
data class GeneratedDesign(
    val id: String,
    val title: String,
    val htmlPath: String,
    val rationale: String,
    val createdAt: String,
)
