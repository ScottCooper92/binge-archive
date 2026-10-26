package com.cooper.binge.companion.archive.ui.status

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.archive.R
import com.cooper.binge.companion.archive.theme.ArchiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The phone's layout follows the window: one screen below 840dp, the list and its detail from there up. */
@RunWith(RobolectricTestRunner::class)
class PhoneStatusRootTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id)

    private val ready =
        StatusUiState.Ready(
            filmCount = 1,
            lastRefreshedAt = null,
            lastRefreshFailed = false,
            refreshing = false,
            recent = emptyList(),
            reported = emptyList(),
        )

    private fun show(
        state: StatusUiState = ready,
        actions: StatusActions = StatusActions(),
        showingLicences: Boolean = false,
    ) = compose.setContent {
        ArchiveTheme { PhoneStatusRoot(state, actions, showingLicences, onCloseLicences = {}, now = 0L) }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `a wide window shows the list with Sources open beside it, and the licence row opens the licences`() {
        val opened = mutableListOf<String>()
        show(actions = StatusActions(onOpenLicences = { opened += "licences" }, onOpenSources = { opened += "sources" }))

        compose.onNodeWithText(text(R.string.app_label)).assertExists()
        compose.onNodeWithText(text(R.string.settings_title_matches)).assertExists()
        compose.onNodeWithText(text(R.string.status_licences_row)).performClick()
        compose.onNode(hasText(text(R.string.settings_title)) and hasClickAction()).performClick()

        assertEquals(listOf("licences", "sources"), opened)
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `a wide window shows the licence page beside the list when it is open`() {
        show(showingLicences = true)

        compose.onNodeWithText(text(R.string.app_label)).assertExists()
        compose.onNodeWithText(text(R.string.licences_intro)).assertExists()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `a wide window still fills itself while the state loads`() {
        show(state = StatusUiState.Loading)

        compose.onNodeWithText(text(R.string.app_label)).assertExists()
        compose.onNodeWithText(text(R.string.settings_title)).assertDoesNotExist()
    }

    @Test
    fun `a phone shows one screen, with Sources inline, or the licence page alone`() {
        show()

        compose.onNodeWithText(text(R.string.settings_title_matches)).assertExists()
        compose.onNodeWithText(text(R.string.licences_intro)).assertDoesNotExist()
    }

    @Test
    fun `a phone with the licence page open shows only that page`() {
        show(showingLicences = true)

        compose.onNodeWithText(text(R.string.licences_intro)).assertExists()
        compose.onNodeWithText(text(R.string.app_label)).assertDoesNotExist()
    }
}
