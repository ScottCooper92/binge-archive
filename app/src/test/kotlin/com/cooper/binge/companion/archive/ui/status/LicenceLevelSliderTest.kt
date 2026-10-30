package com.cooper.binge.companion.archive.ui.status

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.cooper.binge.companion.archive.theme.ArchiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The thumb stays on "Any" while its confirm is open, and returns to the saved level when that is cancelled. */
@RunWith(RobolectricTestRunner::class)
class LicenceLevelSliderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun label(level: LicenceLevel): String {
        val choice = SourceSettingChoices.licences.first { it.value == level }
        return ApplicationProvider.getApplicationContext<android.app.Application>().getString(choice.label)
    }

    private fun assertShows(level: LicenceLevel) {
        compose
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label(level)),
            ).assertExists()
    }

    @Test
    fun `confirming shows Any, and cancelling returns to the saved level`() {
        var confirming by mutableStateOf(true)
        compose.setContent {
            ArchiveTheme {
                LicenceLevelSlider(
                    selected = LicenceLevel.PUBLIC_DOMAIN,
                    confirming = confirming,
                    onSelect = {},
                )
            }
        }

        assertShows(LicenceLevel.ANY)
        confirming = false
        compose.waitForIdle()
        assertShows(LicenceLevel.PUBLIC_DOMAIN)
    }
}
