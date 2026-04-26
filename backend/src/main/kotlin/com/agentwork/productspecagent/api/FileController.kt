package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.FileContent
import com.agentwork.productspecagent.domain.FileEntry
import com.agentwork.productspecagent.service.ProjectNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.nio.file.Path

@RestController
@RequestMapping("/api/v1/projects/{projectId}/files")
class FileController(
    @Value("\${app.data-path}") private val dataPath: String
) {
    private fun projectDir(projectId: String): Path =
        Path.of(dataPath, "projects", projectId)

    @GetMapping
    fun listFiles(@PathVariable projectId: String): List<FileEntry> {
        val dir = projectDir(projectId)
        if (!Files.exists(dir)) throw ProjectNotFoundException(projectId)
        return buildTree(dir, "")
    }

    @GetMapping("/**")
    fun readFile(
        @PathVariable projectId: String,
        request: jakarta.servlet.http.HttpServletRequest
    ): FileContent {
        val dir = projectDir(projectId)
        val prefix = "/api/v1/projects/$projectId/files/"
        val cleanPath = request.requestURI.removePrefix(prefix).removePrefix("/")

        val file = dir.resolve(cleanPath)

        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw ProjectNotFoundException("File not found: $cleanPath")
        }

        // Security: ensure path doesn't escape project dir
        if (!file.normalize().startsWith(dir.normalize())) {
            throw ProjectNotFoundException("Invalid path: $cleanPath")
        }

        val name = file.fileName.toString()
        if (isBinary(name)) {
            return FileContent(
                path = cleanPath, name = name, content = "",
                language = "binary", lineCount = 0, binary = true
            )
        }

        val content = Files.readString(file)
        return FileContent(
            path = cleanPath,
            name = name,
            content = content,
            language = detectLanguage(name),
            lineCount = content.lines().size
        )
    }

    private fun buildTree(dir: Path, relativePath: String): List<FileEntry> {
        if (!Files.exists(dir)) return emptyList()

        val entries = mutableListOf<FileEntry>()

        Files.list(dir).use { stream ->
            stream.sorted(Comparator.comparing<Path, Boolean> { !Files.isDirectory(it) }
                .thenComparing { it.fileName.toString() })
                .forEach { path ->
                    val name = path.fileName.toString()
                    val relPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

                    if (Files.isDirectory(path)) {
                        entries.add(FileEntry(
                            name = name,
                            path = relPath,
                            isDirectory = true,
                            children = buildTree(path, relPath)
                        ))
                    } else {
                        entries.add(FileEntry(
                            name = name,
                            path = relPath,
                            isDirectory = false,
                            size = Files.size(path)
                        ))
                    }
                }
        }

        return entries
    }

    private fun isBinary(filename: String): Boolean {
        val lower = filename.lowercase()
        return BINARY_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun detectLanguage(filename: String): String = when {
        filename.endsWith(".kt") -> "kotlin"
        filename.endsWith(".kts") -> "kotlin"
        filename.endsWith(".groovy") || filename.endsWith(".gradle") -> "groovy"
        filename.endsWith(".json") -> "json"
        filename.endsWith(".yaml") || filename.endsWith(".yml") -> "yaml"
        filename.endsWith(".md") -> "markdown"
        filename.endsWith(".properties") -> "properties"
        filename.endsWith(".xml") -> "xml"
        filename.endsWith(".ts") || filename.endsWith(".tsx") -> "typescript"
        filename.endsWith(".js") || filename.endsWith(".jsx") -> "javascript"
        filename.endsWith(".css") -> "css"
        filename.endsWith(".html") -> "html"
        filename == "Dockerfile" -> "dockerfile"
        filename == ".gitignore" -> "text"
        else -> "text"
    }

    companion object {
        private val BINARY_EXTENSIONS = setOf(
            ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".zip"
        )
    }
}
