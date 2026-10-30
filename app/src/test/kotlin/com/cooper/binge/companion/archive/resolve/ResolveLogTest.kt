package com.cooper.binge.companion.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ResolveLogTest {
    @Test
    fun `a debug build logs to logcat and a release build logs nothing`() {
        assertSame(LogcatResolveLog, resolveLogFor(debug = true))
        assertSame(ResolveLog.NONE, resolveLogFor(debug = false))
    }

    @Test
    fun `the none log accepts a line and does nothing`() {
        ResolveLog.NONE.line { error("never assembled") }
    }

    @Test
    fun `an http failure is worded by its status, never by its message`() {
        val failure = HttpStatusException(403, "archive.org")

        assertEquals("read id=item failed http=403", ResolveEvents.read("item", failure))
        assertFalse(ResolveEvents.failed(failure, 12).contains("archive.org"))
    }

    @Test
    fun `any other failure is worded by its class, never by its message`() {
        val failure = IOException("https://archive.org/metadata/secret-item timed out")

        val line = ResolveEvents.read("item", failure)

        assertEquals("read id=item failed io=IOException", line)
        assertFalse(line.contains("://"))
    }

    @Test
    fun `a read that worked says so`() {
        assertEquals("read id=item ok", ResolveEvents.read("item", null))
    }

    @Test
    fun `the lines carry only numbers, identifiers and reason words`() {
        val lines =
            listOf(
                ResolveEvents.start(933260, "ANY", titleMatches = true),
                ResolveEvents.candidates(Route.TITLE, listOf("a", "b"), fromIndex = false),
                ResolveEvents.candidates(null, emptyList(), fromIndex = false),
                ResolveEvents.dropped("a", "restricted"),
                ResolveEvents.answer("not_found", 4273),
            )

        assertEquals(
            listOf(
                "resolve start tmdb=933260 level=ANY titleMatches=true",
                "candidates route=title count=2 fromIndex=false ids=[a, b]",
                "candidates route=none count=0 fromIndex=false ids=[]",
                "dropped id=a why=restricted",
                "answer not_found after 4273ms",
            ),
            lines,
        )
        assertTrue(lines.none { "://" in it })
    }
}
