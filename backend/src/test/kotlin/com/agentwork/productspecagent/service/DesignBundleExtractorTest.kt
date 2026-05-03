package com.agentwork.productspecagent.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
