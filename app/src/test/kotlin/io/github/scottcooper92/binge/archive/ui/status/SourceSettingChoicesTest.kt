package io.github.scottcooper92.binge.archive.ui.status

import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import io.github.scottcooper92.binge.archive.settings.MinimumQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSettingChoicesTest {
    @Test
    fun `every licence level and quality is offered once, narrowest first`() {
        assertEquals(LicenceLevel.entries, SourceSettingChoices.licences.map { it.value })
        assertEquals(MinimumQuality.entries, SourceSettingChoices.qualities.map { it.value })
    }

    @Test
    fun `each choice has its own icon`() {
        assertEquals(
            3,
            SourceSettingChoices.licences
                .map { it.icon }
                .distinct()
                .size,
        )
        assertEquals(
            3,
            SourceSettingChoices.qualities
                .map { it.icon }
                .distinct()
                .size,
        )
    }
}
