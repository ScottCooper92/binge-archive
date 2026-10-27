package com.cooper.binge.companion.archive.ui.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.cooper.binge.companion.archive.ui.status.StatusActions
import com.cooper.binge.companion.archive.ui.status.StatusUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Clearing the watch progress on the television changes its row in place, so the remote's focus stays on it. */
@RunWith(RobolectricTestRunner::class)
class TvWatchProgressFocusTest {
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
    fun `clearing keeps the focus on the row, which then says there is nothing in progress`() {
        var titles by mutableIntStateOf(3)
        var clears = 0
        val actions =
            StatusActions(
                onClearProgress = {
                    clears++
                    titles = 0
                },
            )
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            ArchiveTvTheme { TvStatusScreen(ready.copy(progressTitles = titles), actions, now = 0L) }
        }

        // A remote is a keyboard to Compose: out of touch mode, a row can take focus.
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val clear = compose.onNodeWithText(text(R.string.status_progress_clear))
        clear.performSemanticsAction(SemanticsActions.RequestFocus)
        clear.assertIsFocused()
        clear.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()

        assertEquals(1, clears)
        val empty = compose.onNodeWithText(text(R.string.status_progress_empty))
        empty.assertIsFocused()

        // With nothing held, the row still takes focus and asks for nothing.
        empty.performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, clears)
    }
}
