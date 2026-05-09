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
    fun `rejects html entity encoded external urls`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src="https&#x3a;//example.com/a.png">""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src="https&colon;//example.com/a.png">""")
        }
    }

    @Test
    fun `rejects blank html`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("   ")
        }
    }

    @Test
    fun `rejects html over 500 kb`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("a".repeat(500_001))
        }
    }

    @Test
    fun `rejects html over 500 kb by utf 8 byte size`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("é".repeat(250_001))
        }
    }

    @Test
    fun `rejects protocol relative urls`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src="//example.com/a.png">""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<a href='//example.com'>Example</a>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img src=//example.com/a.png>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<a href=//example.com>Example</a>""")
        }
    }

    @Test
    fun `rejects external urls in srcset and css imports`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<img srcset="//example.com/a.png 1x">""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<style>.hero{background-image:url(//example.com/a.png)}</style>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<style>@import "//example.com/a.css";</style>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<style>@import url(https://example.com/a.css);</style>""")
        }
    }

    @Test
    fun `rejects network apis`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>fetch('/api/v1/projects')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>new XMLHttpRequest()</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>new WebSocket('ws://example.com')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>new EventSource('/events')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>window['fetch']('/api/v1/projects')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>navigator.sendBeacon('/api/v1/projects')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>import('/x.js')</script>""")
        }
    }

    @Test
    fun `rejects browser storage and parent access`() {
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>window.parent.postMessage(localStorage.token, '*')</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>sessionStorage.token</script>""")
        }
        assertFailsWith<InvalidDesignPreviewException> {
            validator.validate("""<script>document.cookie</script>""")
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
