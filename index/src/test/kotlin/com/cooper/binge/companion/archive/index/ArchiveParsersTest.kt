package com.cooper.binge.companion.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ArchiveParsersTest {
    @Test
    fun `an IMDb id is read whether the item has one external id or several`() {
        val page = ArchiveParsers.imdbSearchPage(fixture("archive-imdb-search.json"))

        assertEquals(
            listOf(
                ArchiveImdbItem("silent-aelita-queen-of-mars", "tt0014646"),
                ArchiveImdbItem("dvd_the-glass-tomb_montgomery-tully-richard-h-landau-ae-m", "tt0048123"),
                ArchiveImdbItem("dvd_the-trial_orson-welles-pierre-cholot-franz-kafka", "tt0057427"),
            ),
            page.items,
        )
        assertEquals(4, page.numFound)
        assertEquals(4, page.docs)
    }

    @Test
    fun `a response that is not the search answer is a failure`() {
        assertThrows(IOException::class.java) { ArchiveParsers.imdbSearchPage("<html>busy</html>") }
        assertThrows(IOException::class.java) { ArchiveParsers.imdbSearchPage("[]") }
        assertThrows(IOException::class.java) { ArchiveParsers.imdbSearchPage("{}") }
        assertThrows(IOException::class.java) { ArchiveParsers.imdbSearchPage("""{"response":{}}""") }
        assertThrows(IOException::class.java) { ArchiveParsers.imdbSearchPage("""{"response":{"docs":[]}}""") }
    }

    @Test
    fun `a doc with no identifier is skipped`() {
        val page =
            ArchiveParsers.imdbSearchPage(
                """{"response":{"numFound":2,"docs":[{"external-identifier":"urn:imdb:tt0000001"},"x"]}}""",
            )

        assertEquals(emptyList<ArchiveImdbItem>(), page.items)
    }
}
