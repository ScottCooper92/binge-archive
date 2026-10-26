package com.cooper.binge.companion.archive.ui.status

import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.cooper.binge.companion.archive.settings.MinimumQuality
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
            4,
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
