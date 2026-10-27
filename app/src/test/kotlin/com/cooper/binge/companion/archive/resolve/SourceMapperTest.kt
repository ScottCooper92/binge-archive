package com.cooper.binge.companion.archive.resolve

import com.binge.companion.contracts.stream.v1.LicenceKind
import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.stream.v1.StreamSource
import com.cooper.binge.companion.archive.settings.MinimumQuality
import com.cooper.binge.companion.archive.settings.SourceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals("Night of the Living Dead (1968)", it.label)
            assertEquals("Public domain (US)", it.licenceLabel)
            assertEquals(LicenceKind.LICENCE_KIND_OPEN, it.licenceKind)
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
        assertEquals("PhantasmagoriaTheater-Nosferatu1922909_512kb.mp4", source.fileName)
        assertEquals("Nosferatu (1922)", source.label)
        assertEquals("Public domain (US)", source.licenceLabel)
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
        assertEquals("Film", source.label)
    }

    private fun onlyFile(name: String): StreamSource {
        val file = ArchiveFile(name, "h.264", 1, 480, 100.0)
        val item = ArchiveItem("item", "movies", "Film", null, null, accessRestricted = false, dark = false, files = listOf(file))
        return sources(item, TitleFacts.NONE).single()
    }

    @Test
    fun `file_name is the Archive file's name, and the label stays the title`() {
        val source = onlyFile("Nosferatu_1922_512kb.mp4")

        assertEquals("Nosferatu_1922_512kb.mp4", source.fileName)
        assertEquals("Film", source.label)
        assertEquals("item/Nosferatu_1922_512kb.mp4", source.id)
        assertEquals("https://archive.org/download/item/Nosferatu_1922_512kb.mp4", source.source.url)
    }

    @Test
    fun `file_name is the bare name, without the file's folder`() {
        val source = onlyFile("sub/dir/x.mp4")

        assertEquals("x.mp4", source.fileName)
        assertEquals("item/sub/dir/x.mp4", source.id)
        assertEquals("Film", source.label)
    }

    @Test
    fun `a long file name is cut to 120 characters, and never cuts a character in two`() {
        val source = onlyFile("A".repeat(196) + ".mp4")

        assertEquals(SourceMapper.MAX_FILE_NAME, source.fileName.length)
        assertEquals("A".repeat(120), source.fileName)
        assertEquals("Film", source.label)
        assertFalse(source.label.contains("AAAA"))

        val emoji = "\uD83C\uDFAC"
        assertEquals("a".repeat(119) + emoji, SourceMapper.fileName("a".repeat(119) + emoji + "tail.mp4"))
    }

    private fun withCaptions(
        itemLanguage: String?,
        vararg captions: Pair<String, Long>,
    ): List<StreamSource> {
        val files =
            captions.map { (name, size) -> ArchiveFile(name, "SubRip", size, null, null) } +
                ArchiveFile("film.mp4", "h.264", 1, 480, 100.0)
        val item = ArchiveItem("item", "movies", "Film", null, itemLanguage, accessRestricted = false, dark = false, files = files)
        return sources(item, TitleFacts.NONE)
    }

    private fun languages(source: StreamSource) = source.subtitlesList.map { it.language }

    @Test
    fun `a caption file takes the language its name gives, not the film's`() {
        val source = withCaptions("Spanish", "film.en.srt" to 10, "film.eng.srt" to 11, "film.es.srt" to 12, "film_fra.srt" to 13).single()

        assertEquals(listOf("en", "en", "es", "fr"), languages(source))
    }

    @Test
    fun `a caption file that names no language takes the film's, and undetermined when that is unknown too`() {
        assertEquals(listOf("es"), languages(withCaptions("Spanish", "film.srt" to 10).single()))
        assertEquals(listOf("und"), languages(withCaptions(null, "film.srt" to 10).single()))
        assertEquals(listOf("und"), languages(withCaptions("Klingon", "film.srt" to 10).single()))
    }

    @Test
    fun `a caption file with a language in its name needs no language on the film`() {
        assertEquals(listOf("en"), languages(withCaptions(null, "film.en.srt" to 10).single()))
        assertEquals(listOf("de"), languages(withCaptions("Klingon", "film-German.srt" to 10).single()))
    }

    @Test
    fun `automatic captions keep the language of the film`() {
        val source = withCaptions("French", "film.asr.srt" to 10).single()

        assertEquals(listOf("fr"), languages(source))
        assertEquals("Automatic captions", source.subtitlesList.single().label)
    }

    @Test
    fun `captions of the same size in two languages are two tracks, and one language uploaded twice is one`() {
        val source = withCaptions("English", "film.en.srt" to 10, "film.es.srt" to 10, "film2.en.srt" to 10).single()

        assertEquals(listOf("en", "es"), languages(source))
    }

    @Test
    fun `a word that only looks like a language is not one`() {
        // "it" is Italian, but "Hit" is not "it": the word has to be the whole last piece of the name.
        assertEquals(listOf("en"), languages(withCaptions("English", "The.Hit.srt" to 10).single()))
        assertEquals(listOf("und"), languages(withCaptions(null, "Spanishwords.srt" to 10).single()))
    }

    @Test
    fun `a Spanish film with English captions is kept on an English device and dropped on a Spanish one`() {
        val sources = withCaptions("Spanish", "film.en.srt" to 10)
        val mine = SourceSettings(subtitlesInMyLanguage = true)

        assertEquals(sources, SourceMapper(testStrings, deviceLanguage = { "en" }).kept(sources, mine))
        assertEquals(emptyList<Any>(), SourceMapper(testStrings, deviceLanguage = { "es" }).kept(sources, mine))
    }

    @Test
    fun `captions of undetermined language never count as the device's language`() {
        val sources = withCaptions(null, "film.srt" to 10)
        val mine = SourceSettings(subtitlesInMyLanguage = true)

        assertEquals(emptyList<Any>(), SourceMapper(testStrings, deviceLanguage = { "en" }).kept(sources, mine))
        assertEquals(sources, SourceMapper(testStrings, deviceLanguage = { "en" }).kept(sources, SourceSettings.DEFAULT))
    }

    @Test
    fun `a long title is shortened so the label fits 120 characters, and the label names no licence`() {
        val label = testStrings.label("A".repeat(200), 1922, SourceMapper.MAX_LABEL)

        assertEquals(120, label.length)
        assertTrue(label.endsWith("… (1922)"))
        assertEquals(120, testStrings.label("A".repeat(200), null, SourceMapper.MAX_LABEL).length)
        assertEquals("Film", testStrings.label("Film", null, SourceMapper.MAX_LABEL))
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
    fun `each licence kind goes on the wire with its licence label`() {
        fun wire(licence: Licence) = testStrings.licenceName(licence) to licence.kind.onTheWire()

        assertEquals("Public domain" to LicenceKind.LICENCE_KIND_OPEN, wire(Licence(Licence.Kind.PUBLIC_DOMAIN_MARK)))
        assertEquals("CC0" to LicenceKind.LICENCE_KIND_OPEN, wire(Licence(Licence.Kind.CC0)))
        assertEquals("CC BY-NC-SA 4.0" to LicenceKind.LICENCE_KIND_OPEN, wire(Licence(Licence.Kind.CC_BY_NC_SA, "4.0")))
        assertEquals("No licence stated" to LicenceKind.LICENCE_KIND_NONE_STATED, wire(Licence(Licence.Kind.NONE_STATED)))
        assertEquals(
            "All rights reserved" to LicenceKind.LICENCE_KIND_RESTRICTED,
            wire(Licence(Licence.Kind.RESTRICTED, "All rights reserved")),
        )
        assertEquals("Free to view" to LicenceKind.LICENCE_KIND_UNKNOWN, wire(Licence(Licence.Kind.UNKNOWN, "Free to view")))
        Licence.Kind.entries.forEach { assertTrue(it.name, it.onTheWire() != LicenceKind.LICENCE_KIND_UNSPECIFIED) }
    }

    @Test
    fun `an item's own licence text is shortened to the host's 40 characters`() {
        val own = Licence(Licence.Kind.RESTRICTED, "Copyright 1931 Universal Pictures Company, Inc. All rights reserved worldwide.")

        val name = testStrings.licenceName(own)

        assertEquals(SourceStrings.MAX_LICENCE_LABEL, name.length)
        assertEquals("Copyright 1931 Universal Pictures Compa…", name)
    }

    @Test
    fun `shortening never cuts a character in two`() {
        val emoji = "\uD83C\uDFAC"
        val text = "a".repeat(38) + emoji + "tail"

        val short = text.shortened(40)

        assertEquals("a".repeat(38) + "…", short)
        assertEquals("short", "short".shortened(40))
    }

    @Test
    fun `file names are encoded into the download URL`() {
        assertEquals(
            "https://archive.org/download/my_item/dir/A%20Film%20(2).mp4",
            SourceMapper.downloadUrl("my_item", "dir/A Film (2).mp4"),
        )
    }
}
