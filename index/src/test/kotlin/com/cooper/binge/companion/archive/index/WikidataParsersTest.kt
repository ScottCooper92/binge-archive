package com.cooper.binge.companion.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class WikidataParsersTest {
    private val nonFilmClasses = WikidataParsers.nonFilmClasses(fixture("wikidata-non-film-classes.tsv"))

    @Test
    fun `the link query's rows become links, films, series and non-films`() {
        val links = WikidataParsers.links(fixture("wikidata-links.tsv"), nonFilmClasses)

        assertEquals(
            listOf(
                // A "video": not a known non-film kind, so it may be a film (#26).
                WikidataLink(LinkedMediaType.MOVIE, 34372, "Execution_of_Mary_1895", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 653, "Nosferatu_DVD_quality", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 653, "PhantasmagoriaTheater-Nosferatu1922909", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true),
                WikidataLink(LinkedMediaType.TV, 894, "The_Prisoner", isFilm = false),
                // A television series episode, and nothing else: not a film.
                WikidataLink(LinkedMediaType.MOVIE, 146742, "silent-a-w-o-l-", isFilm = false),
                // An episode, but a television special too, which is not a known non-film kind.
                WikidataLink(LinkedMediaType.MOVIE, 490096, "jack-levine-a-feast-of-pure-reason-1986", isFilm = true),
            ),
            links,
        )
    }

    @Test
    fun `a row with a malformed TMDB id is skipped, not fatal`() {
        val links = WikidataParsers.links(fixture("wikidata-links.tsv"), nonFilmClasses)

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

        val imdb = "?imdb\t?tmdb\t?nonFilm\n\"nm0000001\"\t\"1\"\t\"false\"\n"
        assertEquals(emptyList<ImdbTmdbLink>(), WikidataParsers.imdbLinks(imdb))
    }

    @Test
    fun `a link is not a film only when every class is a known non-film kind, and a series never is`() {
        val series = "<http://www.wikidata.org/entity/Q5398426>"
        val tsv =
            "?type\t?tmdb\t?ia\t?class\n" +
                "\"movie\"\t\"1\"\t\"a\"\t$series\n" +
                "\"movie\"\t\"1\"\t\"a\"\t<http://www.wikidata.org/entity/Q1261214>\n" +
                "\"movie\"\t\"2\"\t\"b\"\t$series\n" +
                "\"movie\"\t\"3\"\t\"c\"\t\n" +
                "\"tv\"\t\"4\"\t\"d\"\t<http://www.wikidata.org/entity/Q11424>\n"

        assertEquals(
            listOf(
                WikidataLink(LinkedMediaType.MOVIE, 1, "a", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 2, "b", isFilm = false),
                WikidataLink(LinkedMediaType.MOVIE, 3, "c", isFilm = true),
                WikidataLink(LinkedMediaType.TV, 4, "d", isFilm = false),
            ),
            WikidataParsers.links(tsv, setOf("http://www.wikidata.org/entity/Q5398426")),
        )
    }

    @Test
    fun `only the five known non-film kinds and their subclasses are non-film classes`() {
        assertEquals(
            setOf("Q1259759", "Q21191270", "Q24856", "Q5398426", "Q56884562", "Q7725634")
                .map { "http://www.wikidata.org/entity/$it" }
                .toSet(),
            nonFilmClasses,
        )
        // A television pilot is a television series episode, but a film too: not a non-film kind.
        assertEquals(false, "http://www.wikidata.org/entity/Q653916" in nonFilmClasses)
    }

    @Test
    fun `an IMDb link Wikidata calls a known non-film kind is not a film`() {
        val imdb = "?imdb\t?tmdb\t?nonFilm\n\"tt0000001\"\t\"1\"\t\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>\n"

        assertEquals(listOf(ImdbTmdbLink("tt0000001", 1, isFilm = false)), WikidataParsers.imdbLinks(imdb))
    }

    @Test
    fun `no links at all is a failure, never an empty index`() {
        assertThrows(IOException::class.java) { WikidataParsers.checkNotEmpty(emptyList()) }
    }
}
