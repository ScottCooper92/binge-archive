package io.github.scottcooper92.binge.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SeedFormatTest {
    private val snapshot =
        IndexSnapshot(
            links =
                listOf(
                    WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true),
                    WikidataLink(LinkedMediaType.TV, 894, "The_Prisoner", isFilm = false),
                ),
            archiveItems = listOf(ArchiveImdbItem("silent-aelita-queen-of-mars", "tt0014646")),
            imdbLinks = listOf(ImdbTmdbLink("tt0014646", 2973, isFilm = true)),
        )

    private fun read(text: String) = SeedFormat.read(text.reader().buffered())

    @Test
    fun `a written seed reads back as the same snapshot`() {
        val text = buildString { SeedFormat.write(snapshot, this) }

        assertEquals(snapshot, read(text))
    }

    @Test
    fun `a file without the v1 header is refused`() {
        assertThrows(IOException::class.java) { read("") }
        assertThrows(IOException::class.java) { read("L\tmovie\t1\tx\t1\n") }
    }

    @Test
    fun `a malformed record is refused rather than half read`() {
        val header = "# binge-archive index seed v1\n"
        assertThrows(IOException::class.java) { read(header + "X\t1\n") }
        assertThrows(IOException::class.java) { read(header + "L\tfilm\t1\tx\t1\n") }
        assertThrows(IOException::class.java) { read(header + "L\tmovie\t0\tx\t1\n") }
        assertThrows(IOException::class.java) { read(header + "A\tonly-one\n") }
    }

    @Test
    fun `blank lines are ignored`() {
        assertEquals(IndexSnapshot(emptyList(), emptyList(), emptyList()), read("# binge-archive index seed v1\n\n"))
    }
}
