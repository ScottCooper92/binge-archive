package com.cooper.binge.companion.archive.ui.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.archive.R
import com.cooper.binge.companion.archive.theme.ArchiveTvTheme
import com.cooper.binge.companion.archive.ui.status.RecentTitle
import com.cooper.binge.companion.archive.ui.status.StatusActions
import com.cooper.binge.companion.archive.ui.status.StatusUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Clearing Recent or Reported on the television changes its last row in place, so the remote's focus stays on it. */
@RunWith(RobolectricTestRunner::class)
class TvStatusClearFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private val ready =
        StatusUiState.Ready(
            filmCount = 1,
            lastRefreshedAt = null,
            lastRefreshFailed = false,
            refreshing = false,
            recent = emptyList(),
            reported = emptyList(),
        )

    @Test
    fun `clearing Recent keeps the focus on its row, which then says there is nothing yet`() {
        var state by mutableStateOf(
            ready.copy(recent = listOf(RecentTitle(tmdbId = 1, title = "Nosferatu", identifier = "nosferatu", resolvedAt = 0L))),
        )
        var clears = 0
        val actions =
            StatusActions(
                onClearRecent = {
                    clears++
                    state = state.copy(recent = emptyList())
                },
            )
        clearKeepsFocus(
            state = { state },
            actions = actions,
            clearLabel = R.string.status_recent_clear,
            emptyLabel = R.string.status_recent_empty,
            clears = { clears },
        )
    }

    @Test
    fun `clearing Reported keeps the focus on its row, which then says none are reported`() {
        var state by mutableStateOf(ready.copy(reported = listOf("nosferatu-1922")))
        var clears = 0
        val actions =
            StatusActions(
                onClearReported = {
                    clears++
                    state = state.copy(reported = emptyList())
                },
            )
        clearKeepsFocus(
            state = { state },
            actions = actions,
            clearLabel = R.string.status_reported_clear,
            emptyLabel = R.string.status_reported_empty,
            clears = { clears },
        )
    }

    private fun clearKeepsFocus(
        state: () -> StatusUiState.Ready,
        actions: StatusActions,
        clearLabel: Int,
        emptyLabel: Int,
        clears: () -> Int,
    ) {
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            ArchiveTvTheme { TvStatusScreen(state(), actions, now = 0L) }
        }

        // A remote is a keyboard to Compose: out of touch mode, a row can take focus.
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val clear = compose.onNodeWithText(text(clearLabel))
        clear.performSemanticsAction(SemanticsActions.RequestFocus)
        clear.assertIsFocused()
        clear.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()

        assertEquals(1, clears())
        val empty = compose.onNodeWithText(text(emptyLabel))
        empty.assertIsFocused()

        // With nothing left, the row still takes focus and asks for nothing.
        empty.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, clears())
    }
}
