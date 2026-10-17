package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMapperTest {
    private val mapper = SourceMapper(testStrings, clock = { 1_000_000L })
    private val notld = WikidataTitle.parse(resolveFixture("wikidata-10331.tsv"))

    private fun item(name: String) = ArchiveMetadata.parse(name, resolveFixture("metadata-$name.json"))

    private fun licensed(
        item: ArchiveItem,
        downloads: Long = 0,
    ) = LicensedItem(item, Licence(Licence.Kind.PUBLIC_DOMAIN_US), downloads)

    @Test
    fun `Night of the Living Dead maps to its 1080p, 720p and SD h264 encodes`() {
        val sources = mapper.map(listOf(licensed(item("Night.Of.The.Living.Dead_1080p"))), notld)

        assertEquals(
            listOf(
                Triple(Resolution.RESOLUTION_HD_1080, "NightOfTheLivingDead_PS3.mp4", "video/mp4"),
                Triple(Resolution.RESOLUTION_HD_720, "NightOfTheLivingDead_720p.mkv", "video/x-matroska"),
                Triple(Resolution.RESOLUTION_SD, "NightOfTheLivingDead_720p.mp4", "video/mp4"),
            ),
            sources.map { Triple(it.quality.resolution, it.id.substringAfter('/'), it.source.mimeType) },
        )
        sources.forEach {
            assertEquals("H.264", it.quality.videoCodec)
            assertEquals("Night of the Living Dead (1968) · Public domain (US)", it.label)
            assertTrue(it.source.url.startsWith("https://archive.org/download/Night.Of.The.Living.Dead_1080p/"))
            assertEquals(1_000L + 24 * 60 * 60, it.source.expiresAt.seconds)
        }
        assertEquals(1862510336L, sources.first().quality.sizeBytes)
    }

    @Test
    fun `the SubRip captions become one English track on every source`() {
        val sources = mapper.map(listOf(licensed(item("Night.Of.The.Living.Dead_1080p"))), notld)

        sources.forEach { source ->
            val track = source.subtitlesList.single()
            assertEquals("en", track.language)
            assertEquals("Automatic captions", track.label)
            assertTrue(track.url.endsWith(".asr.srt"))
        }
    }

    @Test
    fun `reels of a split upload are not the film`() {
        val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv"))

        assertEquals(emptyList<Any>(), mapper.map(listOf(licensed(item("Nosferatu_DVD_quality"))), nosferatu))
        // Without a runtime nothing says a reel is not the film, so the longest one stands for the class.
        val withoutRuntime = mapper.map(listOf(licensed(item("Nosferatu_DVD_quality"))), nosferatu.copy(runtimeSeconds = null))
        assertEquals(listOf("nosferatu-2of5_512kb.mp4"), withoutRuntime.map { it.id.substringAfter('/') })
    }

    @Test
    fun `an MP4 is preferred over a taller Ogg when neither names h264`() {
        val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv"))

        val source = mapper.map(listOf(licensed(item("PhantasmagoriaTheater-Nosferatu1922909"))), nosferatu).single()

        assertEquals("PhantasmagoriaTheater-Nosferatu1922909_512kb.mp4", source.id.substringAfter('/'))
        assertEquals("Nosferatu (1922) · Public domain (US)", source.label)
        assertEquals("", source.quality.videoCodec)
    }

    @Test
    fun `items go in download order`() {
        val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv")).copy(runtimeSeconds = null)
        val sources =
            mapper.map(
                listOf(
                    licensed(item("PhantasmagoriaTheater-Nosferatu1922909"), downloads = 1),
                    licensed(item("Nosferatu_DVD_quality"), downloads = 9),
                ),
                nosferatu,
            )

        assertEquals("Nosferatu_DVD_quality", sources.first().id.substringBefore('/'))
        assertEquals("PhantasmagoriaTheater-Nosferatu1922909", sources.last().id.substringBefore('/'))
    }

    @Test
    fun `an item this device reported goes after every other`() {
        val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv")).copy(runtimeSeconds = null)
        val items =
            listOf(
                licensed(item("PhantasmagoriaTheater-Nosferatu1922909"), downloads = 1),
                licensed(item("Nosferatu_DVD_quality"), downloads = 9),
            )

        val sources = mapper.map(items, nosferatu, deranked = setOf("Nosferatu_DVD_quality"))

        assertEquals("PhantasmagoriaTheater-Nosferatu1922909", sources.first().id.substringBefore('/'))
    }

    @Test
    fun `at most fifty sources and ten subtitle tracks`() {
        val files =
            (1..12).map { ArchiveFile("subs$it.srt", "SubRip", it.toLong(), null, null) } +
                ArchiveFile("film.mp4", "h.264", 1, 480, 100.0)
        val items =
            (1..60).map { n ->
                licensed(ArchiveItem("item$n", "movies", "Film", null, null, accessRestricted = false, dark = false, files = files))
            }

        val sources = mapper.map(items, TitleFacts.NONE)

        assertEquals(50, sources.size)
        assertEquals(10, sources.first().subtitlesCount)
        assertEquals(
            "und",
            sources
                .first()
                .subtitlesList
                .first()
                .language,
        )
        assertEquals("Film · Public domain (US)", sources.first().label)
    }

    @Test
    fun `a long title is shortened so the label fits 120 characters`() {
        val label = testStrings.label("A".repeat(200), 1922, Licence(Licence.Kind.CC_BY, "4.0"), SourceMapper.MAX_LABEL)

        assertEquals(120, label.length)
        assertTrue(label.endsWith("… (1922) · CC BY 4.0"))
    }

    @Test
    fun `file names are encoded into the download URL`() {
        assertEquals(
            "https://archive.org/download/my_item/dir/A%20Film%20(2).mp4",
            SourceMapper.downloadUrl("my_item", "dir/A Film (2).mp4"),
        )
    }
}
