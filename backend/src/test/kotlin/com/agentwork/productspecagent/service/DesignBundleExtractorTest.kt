package com.agentwork.productspecagent.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.assertThrows

class DesignBundleExtractorTest {

    private val extractor = DesignBundleExtractor(
        com.agentwork.productspecagent.config.DesignBundleProperties()
    )

    @Test
    fun `parsePages extracts DCSection and DCArtboard tags from inline babel script`() {
        val html = """
            <!doctype html>
            <html><body>
            <script type="text/babel">
              const App = () => (
                <DesignCanvas title="Scheduler">
                  <DCSection id="auth" title="Login" subtitle="Geteiltes Layout">
                    <DCArtboard id="login" label="E · Login" width={1440} height={900}>
                      <Frame><LoginView/></Frame>
                    </DCArtboard>
                  </DCSection>
                  <DCSection id="primary" title="Buchung">
                    <DCArtboard id="table" label="A · Tabelle" width={1440} height={900}>
                      <Frame><TableView/></Frame>
                    </DCArtboard>
                    <DCArtboard id="timeline" label="B · Timeline" width={1440} height={900}>
                      <Frame><TimelineView/></Frame>
                    </DCArtboard>
                  </DCSection>
                </DesignCanvas>
              );
            </script>
            </body></html>
        """.trimIndent()

        val pages = extractor.parsePages(html)

        assertThat(pages).hasSize(3)
        assertThat(pages[0].id).isEqualTo("login")
        assertThat(pages[0].label).isEqualTo("E · Login")
        assertThat(pages[0].sectionId).isEqualTo("auth")
        assertThat(pages[0].sectionTitle).isEqualTo("Login")
        assertThat(pages[0].width).isEqualTo(1440)
        assertThat(pages[0].height).isEqualTo(900)
        assertThat(pages[1].id).isEqualTo("table")
        assertThat(pages[1].sectionId).isEqualTo("primary")
        assertThat(pages[2].id).isEqualTo("timeline")
        assertThat(pages[2].sectionId).isEqualTo("primary")
    }

    @Test
    fun `parsePages returns empty list when no DCArtboard present`() {
        val pages = extractor.parsePages("<html><body><h1>not a canvas</h1></body></html>")
        assertThat(pages).isEmpty()
    }

    @Test
    fun `findEntryHtml prefers HTML containing design-canvas script tag`() {
        val candidates = mapOf(
            "Komponenten-Breakdown.html" to "<html><body>just docs</body></html>",
            "Scheduler.html" to """<html><head><script src="design-canvas.jsx"></script></head></html>""",
        )
        val entry = extractor.findEntryHtml(candidates)
        assertThat(entry).isEqualTo("Scheduler.html")
    }

    @Test
    fun `findEntryHtml falls back to first HTML alphabetically when no canvas marker`() {
        val candidates = mapOf(
            "z-other.html" to "<html></html>",
            "a-first.html" to "<html></html>",
        )
        val entry = extractor.findEntryHtml(candidates)
        assertThat(entry).isEqualTo("a-first.html")
    }
}

class DesignBundleExtractorIntegrationTest {

    private val extractor = DesignBundleExtractor(
        com.agentwork.productspecagent.config.DesignBundleProperties()
    )

    @Test
    fun `extract Scheduler zip yields 5 pages and Scheduler html as entry`() {
        val bytes = java.io.File("../examples/Scheduler.zip").readBytes()
        val out = extractor.extract(bytes, originalFilename = "Scheduler.zip")
        assertThat(out.bundle.entryHtml).isEqualTo("Scheduler.html")
        assertThat(out.bundle.pages).hasSize(5)
        assertThat(out.bundle.pages.map { it.id })
            .containsExactlyInAnyOrder("login", "table", "timeline", "calendar", "pools")
        assertThat(out.files.keys)
            .contains("Scheduler.html", "design-canvas.jsx", "view-login.jsx", "tokens.css")
        assertThat(out.files.keys.none { it.startsWith("__MACOSX") }).isTrue()
    }

    @Test
    fun `extract rejects path traversal entries`() {
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("../escape.txt"))
                zos.write("nope".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val ex = assertThrows<DesignBundleExtractor.InvalidBundleException> {
            extractor.extract(zipBytes, "evil.zip")
        }
        assertThat(ex.message).contains("path")
    }

    @Test
    fun `extract rejects when extracted size exceeds limit`() {
        val tinyProps = com.agentwork.productspecagent.config.DesignBundleProperties(
            maxExtractedBytes = 100,
        )
        val tinyExtractor = DesignBundleExtractor(tinyProps)
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("big.txt"))
                zos.write(ByteArray(500) { 'x'.code.toByte() })
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        assertThrows<DesignBundleExtractor.InvalidBundleException> {
            tinyExtractor.extract(zipBytes, "bomb.zip")
        }
    }

    @Test
    fun `extract filters MACOSX entries silently`() {
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("Scheduler.html"))
                zos.write("""<html><script src="design-canvas.jsx"></script></html>""".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("__MACOSX/.DS_Store"))
                zos.write("junk".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry(".DS_Store"))
                zos.write("junk".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val out = extractor.extract(zipBytes, "x.zip")
        assertThat(out.files.keys).containsExactly("Scheduler.html")
    }

    @Test
    fun `extract throws when no html present`() {
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("only.css"))
                zos.write("body{}".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val ex = assertThrows<DesignBundleExtractor.InvalidBundleException> {
            extractor.extract(zipBytes, "no-html.zip")
        }
        assertThat(ex.message).contains("html")
    }
}
