package com.cooper.binge.companion.archive

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher opens the surface the device asks for, on the real graph. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ArchiveApp::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun `a phone opens on the status screen, and the licence page and back`() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text(R.string.status_licences_row))).fetchSemanticsNodes().isNotEmpty() }
        // The phone groups its sections under titles; the television's two panes have none.
        compose.onNodeWithText(text(R.string.status_about_title).uppercase()).assertExists()

        compose.onNodeWithText(text(R.string.status_licences_row)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.licences_intro)).assertExists()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.settings_title_matches)).assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = ArchiveApp::class, qualifiers = "television")
class MainActivityTelevisionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun `a television opens on the TV status page, not the phone's`() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text(R.string.status_about_headline))).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText(text(R.string.status_about_title).uppercase()).assertDoesNotExist()
    }
}
