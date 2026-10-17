package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatsTest {
    @Test
    fun `every video format maps to a video MIME type`() {
        mapOf(
            ("h.264" to "a.mp4") to "video/mp4",
            ("512Kb MPEG4" to "a_512kb.mp4") to "video/mp4",
            ("h.264 MPEG4" to "a.mkv") to "video/x-matroska",
            ("h.264 MPEG4" to "a.m2ts") to "video/mp2t",
            ("h.264 MPEG4" to "a.MP4") to "video/mp4",
            ("Ogg Video" to "a.ogv") to "video/ogg",
            ("WebM" to "a.webm") to "video/webm",
            ("MPEG2" to "a.mpg") to "video/mpeg",
            ("Quicktime" to "a.mov") to "video/quicktime",
            ("Matroska" to "a.mkv") to "video/x-matroska",
        ).forEach { (file, mime) ->
            val format = VideoFormats.of(file.first, file.second)
            assertEquals(file.toString(), mime, format?.mimeType)
            assertTrue(format!!.mimeType.startsWith("video/"))
        }
    }

    @Test
    fun `subtitles, images, audio, metadata and unknown formats are not videos`() {
        listOf("SubRip", "Item Tile", "VBR MP3", "Metadata", "Text", "ASR", "h.264 MPEG4", null).forEach {
            assertNull(it, VideoFormats.of(it, "a.unknown"))
        }
    }

    @Test
    fun `only h264 in MP4 is the preferred encode`() {
        assertTrue(VideoFormats.of("h.264", "a.mp4")!!.isH264Mp4)
        assertEquals(false, VideoFormats.of("h.264 MPEG4", "a.mkv")!!.isH264Mp4)
        assertEquals(false, VideoFormats.of("512Kb MPEG4", "a.mp4")!!.isH264Mp4)
    }

    @Test
    fun `heights fall into resolution classes`() {
        assertEquals(Resolution.RESOLUTION_SD, VideoFormats.resolutionOf(480))
        assertEquals(Resolution.RESOLUTION_HD_720, VideoFormats.resolutionOf(720))
        assertEquals(Resolution.RESOLUTION_HD_1080, VideoFormats.resolutionOf(1036))
        assertEquals(Resolution.RESOLUTION_UHD_2160, VideoFormats.resolutionOf(2160))
    }
}
