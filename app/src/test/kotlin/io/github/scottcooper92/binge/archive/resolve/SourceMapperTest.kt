package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution
import io.github.scottcooper92.binge.archive.settings.MinimumQuality
import io.github.scottcooper92.binge.archive.settings.SourceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMapperTest {
    private val mapper = SourceMapper(testStrings, clock = { 1_000_000L })
    private val notld = WikidataTitle.parse(resolveFixture("wikidata-10331.tsv"))

    private fun item(name: String) = ArchiveMetadata.parse(name, resolveFixture("metadata-$name.json"))

    private fun sources(
        item: ArchiveItem,
        facts: TitleFacts,
    ) = mapper.sources(LicensedItem(item, Licence(Licence.Kind.PUBLIC_DOMAIN_US), 0), facts, mapper.expiresAt())

    @Test
    fun `Night of the Living Dead maps to its 1080p, 720p and SD h264 encodes`() {
        val sources = sources(item("Night.Of.The.Living.Dead_1080p"), notld)

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
        val sources = sources(item("Night.Of.The.Living.Dead_1080p"), notld)

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

        assertEquals(emptyList<Any>(), sources(item("Nosferatu_DVD_quality"), nosferatu))
        // Without a runtime nothing says a reel is not the film, so the longest one stands for the class.
        val withoutRuntime = sources(item("Nosferatu_DVD_quality"), nosferatu.copy(runtimeSeconds = null))
        assertEquals(listOf("nosferatu-2of5_512kb.mp4"), withoutRuntime.map { it.id.substringAfter('/') })
    }

    @Test
    fun `an MP4 is preferred over a taller Ogg when neither names h264`() {
        val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv"))

        val source = sources(item("PhantasmagoriaTheater-Nosferatu1922909"), nosferatu).single()

        assertEquals("PhantasmagoriaTheater-Nosferatu1922909_512kb.mp4", source.id.substringAfter('/'))
        assertEquals("Nosferatu (1922) · Public domain (US)", source.label)
        assertEquals("", source.quality.videoCodec)
    }

    @Test
    fun `at most ten subtitle tracks`() {
        val files =
            (1..12).map { ArchiveFile("subs$it.srt", "SubRip", it.toLong(), null, null) } +
                ArchiveFile("film.mp4", "h.264", 1, 480, 100.0)
        val item = ArchiveItem("item", "movies", "Film", null, null, accessRestricted = false, dark = false, files = files)

        val source = sources(item, TitleFacts.NONE).single()

        assertEquals(10, source.subtitlesCount)
        assertEquals("und", source.subtitlesList.first().language)
        assertEquals("Film · Public domain (US)", source.label)
    }

    @Test
    fun `a long title is shortened so the label fits 120 characters`() {
        val label = testStrings.label("A".repeat(200), 1922, Licence(Licence.Kind.CC_BY, "4.0"), SourceMapper.MAX_LABEL)

        assertEquals(120, label.length)
        assertTrue(label.endsWith("… (1922) · CC BY 4.0"))
    }

    @Test
    fun `the default settings keep every source`() {
        val sources = sources(item("Night.Of.The.Living.Dead_1080p"), notld)

        assertEquals(sources, mapper.kept(sources, SourceSettings.DEFAULT))
    }

    @Test
    fun `a minimum quality drops the sources below it`() {
        val sources = sources(item("Night.Of.The.Living.Dead_1080p"), notld)

        fun kept(quality: MinimumQuality) = mapper.kept(sources, SourceSettings(minimumQuality = quality)).map { it.quality.resolution }

        assertEquals(
            listOf(Resolution.RESOLUTION_HD_1080, Resolution.RESOLUTION_HD_720, Resolution.RESOLUTION_SD),
            kept(MinimumQuality.ANY),
        )
        assertEquals(listOf(Resolution.RESOLUTION_HD_1080, Resolution.RESOLUTION_HD_720), kept(MinimumQuality.HD_720))
        assertEquals(listOf(Resolution.RESOLUTION_HD_1080), kept(MinimumQuality.HD_1080))
    }

    @Test
    fun `subtitles in my language keep only sources with a track in the device's language`() {
        val sources = sources(item("Night.Of.The.Living.Dead_1080p"), notld)
        val mine = SourceSettings(subtitlesInMyLanguage = true)

        assertEquals(sources, SourceMapper(testStrings, deviceLanguage = { "en" }).kept(sources, mine))
        assertEquals(emptyList<Any>(), SourceMapper(testStrings, deviceLanguage = { "es" }).kept(sources, mine))
        assertEquals(sources, SourceMapper(testStrings, deviceLanguage = { "es" }).kept(sources, SourceSettings.DEFAULT))
    }

    @Test
    fun `an item with no subtitles is dropped when subtitles in my language are asked for`() {
        val files = listOf(ArchiveFile("film.mp4", "h.264", 1, 480, 100.0))
        val item = ArchiveItem("item", "movies", "Film", null, "English", accessRestricted = false, dark = false, files = files)
        val sources = sources(item, TitleFacts.NONE)

        assertEquals(1, sources.size)
        assertEquals(
            emptyList<Any>(),
            SourceMapper(testStrings, deviceLanguage = {
                "en"
            }).kept(sources, SourceSettings(subtitlesInMyLanguage = true)),
        )
    }

    @Test
    fun `an item with no licence stated says so on its label`() {
        assertEquals(
            "Film (1950) · No licence stated",
            testStrings.label("Film", 1950, Licence(Licence.Kind.NONE_STATED)),
        )
    }

    @Test
    fun `file names are encoded into the download URL`() {
        assertEquals(
            "https://archive.org/download/my_item/dir/A%20Film%20(2).mp4",
            SourceMapper.downloadUrl("my_item", "dir/A Film (2).mp4"),
        )
    }
}
