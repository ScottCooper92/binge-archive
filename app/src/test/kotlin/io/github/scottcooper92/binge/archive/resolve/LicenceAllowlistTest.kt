package io.github.scottcooper92.binge.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicenceAllowlistTest {
    private fun label(url: String?) = LicenceAllowlist.match(url)?.let(testStrings::licenceName)

    @Test
    fun `the four allowed licences, each with its label`() {
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
    fun `every other licence is refused, however close`() {
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
            "",
        ).forEach { assertNull(it, label(it)) }
        assertNull(label(null))
    }
}
