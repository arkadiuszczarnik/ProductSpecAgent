package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.CreateProjectRequest
import com.agentwork.productspecagent.domain.FlowState
import com.agentwork.productspecagent.domain.Project
import com.agentwork.productspecagent.domain.ProjectResponse
import com.agentwork.productspecagent.service.ProjectService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(private val projectService: ProjectService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProject(@RequestBody request: CreateProjectRequest): ProjectResponse {
        return projectService.createProject(request.name)
    }

    @GetMapping
    fun listProjects(): List<Project> {
        return projectService.listProjects()
    }

    @GetMapping("/{id}")
    fun getProject(@PathVariable id: String): ProjectResponse {
        return projectService.getProject(id)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProject(@PathVariable id: String) {
        projectService.deleteProject(id)
    }

    @GetMapping("/{id}/flow")
    fun getFlowState(@PathVariable id: String): FlowState {
        return projectService.getFlowState(id)
    }

    @PostMapping("/{id}/docs/regenerate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun regenerateDocs(@PathVariable id: String) {
        projectService.regenerateDocsScaffold(id)
    }
}
