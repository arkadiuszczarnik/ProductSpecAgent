package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.FileContent
import com.agentwork.productspecagent.domain.FileEntry
import com.agentwork.productspecagent.service.ProjectNotFoundException
import com.agentwork.productspecagent.storage.ObjectStore
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/projects/{projectId}/files")
class FileController(private val objectStore: ObjectStore) {

    private fun projectPrefix(projectId: String) = "projects/$projectId/"
    private fun projectKey(projectId: String, relPath: String) = "projects/$projectId/$relPath"

    @GetMapping
    fun listFiles(@PathVariable projectId: String): List<FileEntry> {
        val prefix = projectPrefix(projectId)
        val keys = objectStore.listKeys(prefix)
        if (keys.isEmpty()) throw ProjectNotFoundException(projectId)
        return buildTree(projectId, keys.map { it.removePrefix(prefix) })
    }

    @GetMapping("/**")
    fun readFile(
        @PathVariable projectId: String,
        request: jakarta.servlet.http.HttpServletRequest
    ): FileContent {
        val urlPrefix = "/api/v1/projects/$projectId/files/"
        val cleanPath = request.requestURI.removePrefix(urlPrefix).removePrefix("/")

        // Security: no path traversal
        if (cleanPath.contains("..")) throw ProjectNotFoundException("Invalid path: $cleanPath")

        val key = projectKey(projectId, cleanPath)
        val bytes = objectStore.get(key) ?: throw ProjectNotFoundException("File not found: $cleanPath")

        val name = cleanPath.substringAfterLast('/')
        if (isBinary(name)) {
            return FileContent(
                path = cleanPath, name = name, content = "",
                language = "binary", lineCount = 0, binary = true
            )
        }

        val content = bytes.toString(Charsets.UTF_8)
        return FileContent(
            path = cleanPath,
            name = name,
            content = content,
            language = detectLanguage(name),
            lineCount = content.lines().size
        )
    }

    private fun buildTree(
        projectId: String,
        relativePaths: List<String>,
        pathPrefix: String = "",
    ): List<FileEntry> {
        val storePrefix = projectPrefix(projectId)
        val entries = mutableListOf<FileEntry>()
        val dirs = mutableSetOf<String>()

        // Collect all intermediate directories
        for (relPath in relativePaths) {
            val parts = relPath.split("/")
            for (depth in 1 until parts.size) {
                dirs.add(parts.take(depth).joinToString("/"))
            }
        }

        // Top-level entries (depth 1)
        val topLevelDirs = dirs.filter { !it.contains("/") }.sorted()
        val topLevelFiles = relativePaths.filter { !it.contains("/") }.sorted()

        for (dir in topLevelDirs) {
            val subPaths = relativePaths
                .filter { it.startsWith("$dir/") }
                .map { it.removePrefix("$dir/") }
            entries.add(FileEntry(
                name = dir,
                // Full path from project root, not just the segment name. The previous
                // post-processing `.map { copy(path = "$dir/${child.path}") }` only fixed
                // direct children — grandchildren kept the wrong relative path, which the
                // frontend then sent back to GET /files/** and got a 404.
                path = "$pathPrefix$dir",
                isDirectory = true,
                children = buildTree(projectId, subPaths, "$pathPrefix$dir/"),
            ))
        }
        for (file in topLevelFiles) {
            val bytes = objectStore.get("$storePrefix$pathPrefix$file") ?: continue
            entries.add(FileEntry(
                name = file,
                path = "$pathPrefix$file",
                isDirectory = false,
                size = bytes.size.toLong()
            ))
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
