package io.github.scottcooper92.binge.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicenceAllowlistTest {
    /** The label [url]'s licence takes when [level] admits it, or null when it does not. */
    private fun label(
        url: String?,
        level: LicenceLevel = LicenceLevel.DEFAULT,
    ) = LicenceAllowlist.identify(url).takeIf { level.admits(it.kind) }?.let(testStrings::licenceName)

    private fun kind(url: String?) = LicenceAllowlist.identify(url).kind

    @Test
    fun `the four default licences, each with its label`() {
        assertEquals("Public domain", label("http://creativecommons.org/publicdomain/mark/1.0/"))
        assertEquals("Public domain (US)", label("http://creativecommons.org/licenses/publicdomain/"))
        assertEquals("CC0", label("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("CC BY 4.0", label("https://creativecommons.org/licenses/by/4.0/"))
        assertEquals("CC BY 3.0 US", label("http://creativecommons.org/licenses/by/3.0/us/"))
    }

    @Test
    fun `cosmetic differences still match`() {
        assertEquals("CC0", label("HTTPS://WWW.creativecommons.org/publicdomain/zero/1.0"))
        assertEquals("CC BY 4.0", label("https://creativecommons.org/licenses/by/4.0/deed.en"))
        assertEquals("CC BY 2.0", label("https://creativecommons.org/licenses/by/2.0/legalcode"))
        assertEquals("Public domain", label(" http://creativecommons.org/publicdomain/mark/1.0/?ref=chooser "))
    }

    @Test
    fun `the default refuses every other licence, however close`() {
        listOf(
            "https://creativecommons.org/licenses/by-sa/4.0/",
            "https://creativecommons.org/licenses/by-nc/4.0/",
            "https://creativecommons.org/licenses/by-nd/3.0/",
            "https://creativecommons.org/licenses/by-nc-sa/3.0/us/",
            "https://creativecommons.org/licenses/by/4.0/extra",
            "https://creativecommons.org/licenses/sampling+/1.0/",
            "https://creativecommons.org/publicdomain/mark/2.0/",
            "https://example.org/creativecommons.org/licenses/publicdomain/",
            "https://creativecommons.org.example.org/licenses/publicdomain/",
            "Public Domain",
            "All rights reserved",
            "",
        ).forEach { assertNull(it, label(it)) }
        assertNull(label(null))
    }

    @Test
    fun `every licence against every level`() {
        val pd = "http://creativecommons.org/publicdomain/mark/1.0/"
        val pdUs = "http://creativecommons.org/licenses/publicdomain/"
        val cc0 = "https://creativecommons.org/publicdomain/zero/1.0/"
        val by = "https://creativecommons.org/licenses/by/4.0/"
        val bySa = "https://creativecommons.org/licenses/by-sa/4.0/"
        val byNc = "https://creativecommons.org/licenses/by-nc/3.0/us/"
        val byNd = "https://creativecommons.org/licenses/by-nd/2.0/"
        val byNcSa = "https://creativecommons.org/licenses/by-nc-sa/4.0/deed.es"
        val byNcNd = "https://creativecommons.org/licenses/by-nc-nd/3.0/legalcode"
        val reserved = "Copyright 1931 Universal Pictures. All rights reserved."
        val unknown = "https://example.org/licence"
        val labels =
            mapOf(
                pd to "Public domain",
                pdUs to "Public domain (US)",
                cc0 to "CC0",
                by to "CC BY 4.0",
                bySa to "CC BY-SA 4.0",
                byNc to "CC BY-NC 3.0 US",
                byNd to "CC BY-ND 2.0",
                byNcSa to "CC BY-NC-SA 4.0",
                byNcNd to "CC BY-NC-ND 3.0",
                null to "No licence stated",
                "" to "No licence stated",
                reserved to "Copyright 1931 Universal Pictures. All…",
                unknown to unknown,
            )
        val publicDomain = setOf(pd, pdUs, cc0)
        val admitted =
            mapOf(
                LicenceLevel.PUBLIC_DOMAIN to publicDomain,
                LicenceLevel.PUBLIC_DOMAIN_AND_CC_BY to publicDomain + by,
                LicenceLevel.CREATIVE_COMMONS to publicDomain + setOf(by, bySa, byNc, byNd, byNcSa, byNcNd),
                LicenceLevel.ANY to labels.keys,
            )

        assertEquals(LicenceLevel.entries.toSet(), admitted.keys)
        for ((level, urls) in admitted) {
            for ((url, name) in labels) {
                assertEquals("$url at $level", name.takeIf { url in urls }, label(url, level))
            }
        }
    }

    @Test
    fun `text that reserves the rights is restricted, anything else unrecognised is unknown`() {
        listOf("All rights reserved", "© 1950 Some Studio", "Copyright 1931 Universal", "(c) 1977 Lucasfilm").forEach {
            assertEquals(it, Licence.Kind.RESTRICTED, kind(it))
        }
        listOf(
            "Public Domain",
            "https://example.org/licence",
            "https://creativecommons.org/licenses/sampling+/1.0/",
            "https://creativecommons.org/licenses/by-xx/4.0/",
            "No known copyright restrictions",
        ).forEach { assertEquals(it, Licence.Kind.UNKNOWN, kind(it)) }
    }

    @Test
    fun `an item's own text keeps its words, with its whitespace collapsed`() {
        assertEquals(
            Licence(Licence.Kind.UNKNOWN, "Free to view, not to sell"),
            LicenceAllowlist.identify("  Free to view,\n  not to sell "),
        )
    }

    @Test
    fun `the default is public domain and CC BY`() {
        assertEquals(LicenceLevel.PUBLIC_DOMAIN_AND_CC_BY, LicenceLevel.DEFAULT)
    }
}
