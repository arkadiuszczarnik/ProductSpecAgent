package com.agentwork.productspecagent.service

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DesignPreviewValidatorTest {
    private val validator = DesignPreviewValidator()

    @Test
    fun `accepts static html css and local inline interaction`() {
        validator.validate(
            """
            <!doctype html>
            <html>
              <head>
                <style>.tab{display:none}.tab.active{display:block}</style>
              </head>
              <body>
                <button onclick="document.querySelector('#a')?.classList.toggle('active')">Toggle</button>
                <section id="a" class="tab active">Hello</section>
              </body>
            </html>
            """.trimIndent(),
        )
    }

    @Test
    fun `rejects external urls`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src="https://example.com/a.png">""")
        }
    }

    @Test
    fun `rejects network apis`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>fetch('/api/v1/projects')</script>""")
        }
    }

    @Test
    fun `rejects browser storage and parent access`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>window.parent.postMessage(localStorage.token, '*')</script>""")
        }
    }

    @Test
    fun `rejects external scripts and form actions`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<form action="/api/v1/projects"><script src="/x.js"></script></form>""")
        }
    }

    @Test
    fun `rejects single quoted and unquoted external script sources`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script src='/x.js'></script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script src=/x.js></script>""")
        }
    }

    @Test
    fun `rejects direct parent access`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>parent.document.body</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>parent.postMessage('x', '*')</script>""")
        }
    }

    @Test
    fun `rejects single quoted and unquoted form actions`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<form action='/api/v1/projects'></form>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<form action=/api/v1/projects></form>""")
        }
    }
}
