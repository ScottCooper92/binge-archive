package com.cooper.binge.companion.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataTitleTest {
    @Test
    fun `a real lookup reads as one set of facts`() {
        assertEquals(
            TitleFacts(listOf("Night.Of.The.Living.Dead_1080p"), "tt0063350", "Night of the Living Dead", 1968, 96 * 60.0),
            WikidataTitle.parse(resolveFixture("wikidata-10331.tsv")),
        )
    }

    @Test
    fun `several release dates keep the earliest year, and ids are not repeated`() {
        val facts = WikidataTitle.parse(resolveFixture("wikidata-653.tsv"))

        assertEquals(1922, facts.year)
        assertEquals(4, facts.archiveIds.size)
    }

    @Test
    fun `a runtime in seconds or hours is converted, and an unknown unit is ignored`() {
        fun runtime(
            amount: String,
            unit: String,
        ) = WikidataTitle
            .parse(
                "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n\t\t\t\t$amount\t<http://www.wikidata.org/entity/$unit>\n",
            ).runtimeSeconds

        assertEquals(5400.0, runtime("5400", "Q11574"))
        assertEquals(5400.0, runtime("1.5", "Q25235"))
        assertEquals(null, runtime("90", "Q1"))
    }

    @Test
    fun `no item at all is no facts`() {
        assertEquals(TitleFacts.NONE, WikidataTitle.parse("?ia\t?imdb\t?title\t?date\t?amount\t?unit\n"))
    }

    @Test
    fun `the query asks for the TMDB id as a literal`() {
        assertTrue(WikidataTitle.query(10331).contains("wdt:P4947 \"10331\""))
    }
}
