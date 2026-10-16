package io.github.scottcooper92.binge.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class WikidataParsersTest {
    private val filmClasses = WikidataParsers.filmClasses(fixture("wikidata-film-classes.tsv"))

    @Test
    fun `the link query's rows become links, films and series both`() {
        val links = WikidataParsers.links(fixture("wikidata-links.tsv"), filmClasses)

        assertEquals(
            listOf(
                WikidataLink(LinkedMediaType.MOVIE, 34372, "Execution_of_Mary_1895", isFilm = false),
                WikidataLink(LinkedMediaType.MOVIE, 653, "Nosferatu_DVD_quality", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 653, "PhantasmagoriaTheater-Nosferatu1922909", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true),
                WikidataLink(LinkedMediaType.TV, 894, "The_Prisoner", isFilm = false),
            ),
            links,
        )
    }

    @Test
    fun `a row with a malformed TMDB id is skipped, not fatal`() {
        val links = WikidataParsers.links(fixture("wikidata-links.tsv"), filmClasses)

        assertEquals(emptyList<WikidataLink>(), links.filter { it.archiveId == "Broken_Row" })
    }

    @Test
    fun `the IMDb join's rows become IMDb links`() {
        assertEquals(
            listOf(
                ImdbTmdbLink("tt0014646", 2973, isFilm = true),
                ImdbTmdbLink("tt0048123", 124525, isFilm = true),
                ImdbTmdbLink("tt0057427", 3009, isFilm = true),
            ),
            WikidataParsers.imdbLinks(fixture("wikidata-imdb.tsv")).sortedBy { it.imdbId },
        )
    }

    @Test
    fun `an unknown media type and a malformed IMDb id are skipped`() {
        val tsv = "?type\t?tmdb\t?ia\t?class\n\"film\"\t\"1\"\t\"x\"\t<Q11424>\n"
        assertEquals(emptyList<WikidataLink>(), WikidataParsers.links(tsv, setOf("Q11424")))

        val imdb = "?imdb\t?tmdb\t?film\n\"nm0000001\"\t\"1\"\t\"true\"\n"
        assertEquals(emptyList<ImdbTmdbLink>(), WikidataParsers.imdbLinks(imdb))
    }

    @Test
    fun `a link is a film when any of its classes is, and a series never is`() {
        val tsv =
            "?type\t?tmdb\t?ia\t?class\n" +
                "\"movie\"\t\"1\"\t\"a\"\t<http://www.wikidata.org/entity/Q1>\n" +
                "\"movie\"\t\"1\"\t\"a\"\t<http://www.wikidata.org/entity/Q11424>\n" +
                "\"tv\"\t\"2\"\t\"b\"\t<http://www.wikidata.org/entity/Q11424>\n"

        assertEquals(
            listOf(
                WikidataLink(LinkedMediaType.MOVIE, 1, "a", isFilm = true),
                WikidataLink(LinkedMediaType.TV, 2, "b", isFilm = false),
            ),
            WikidataParsers.links(tsv, setOf("http://www.wikidata.org/entity/Q11424")),
        )
    }

    @Test
    fun `only the classes marked as films are film classes`() {
        assertEquals(19, filmClasses.size)
        assertEquals(true, "http://www.wikidata.org/entity/Q11424" in filmClasses)
        assertEquals(false, "http://www.wikidata.org/entity/Q98069877" in filmClasses)
    }

    @Test
    fun `no links at all is a failure, never an empty index`() {
        assertThrows(IOException::class.java) { WikidataParsers.checkNotEmpty(emptyList()) }
    }
}
