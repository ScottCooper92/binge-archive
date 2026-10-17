package io.github.scottcooper92.binge.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMetadataTest {
    @Test
    fun `a real item reads with its licence and files`() {
        val item =
            checkNotNull(
                ArchiveMetadata.parse("Night.Of.The.Living.Dead_1080p", resolveFixture("metadata-Night.Of.The.Living.Dead_1080p.json")),
            )

        assertEquals("movies", item.mediaType)
        assertEquals("http://creativecommons.org/licenses/publicdomain/", item.licenseUrl)
        assertEquals("English", item.language)
        assertFalse(item.dark)
        assertFalse(item.accessRestricted)
        val ps3 = item.files.single { it.name == "NightOfTheLivingDead_PS3.mp4" }
        assertEquals(ArchiveFile("NightOfTheLivingDead_PS3.mp4", "h.264 MPEG4", 1862510336, 1080, 5752.81), ps3)
    }

    @Test
    fun `a missing item and a dark one read as dark`() {
        assertTrue(ArchiveMetadata.parse("gone", "{}").dark)
        assertTrue(ArchiveMetadata.parse("dark", """{"is_dark":true}""").dark)
        assertTrue(ArchiveMetadata.parse("dark", """{"metadata":{"is_dark":"true"}}""").dark)
    }

    @Test
    fun `an access-restricted item says so`() {
        assertTrue(ArchiveMetadata.parse("x", """{"metadata":{"access-restricted-item":"true"}}""").accessRestricted)
    }

    @Test
    fun `a body that is not a JSON object fails the load`() {
        assertThrows(java.io.IOException::class.java) { ArchiveMetadata.parse("x", "<html>") }
        assertThrows(java.io.IOException::class.java) { ArchiveMetadata.parse("x", "[1]") }
    }

    @Test
    fun `lengths read as seconds or a clock`() {
        assertEquals(5752.82, ArchiveMetadata.seconds("5752.82")!!, 0.001)
        assertEquals(5752.0, ArchiveMetadata.seconds("95:52")!!, 0.001)
        assertEquals(5706.0, ArchiveMetadata.seconds("01:35:06")!!, 0.001)
        assertNull(ArchiveMetadata.seconds("about an hour"))
        assertNull(ArchiveMetadata.seconds("1:xx"))
        assertNull(ArchiveMetadata.seconds("0"))
    }
}
