package com.cooper.binge.companion.archive.ui.tv

import android.app.Application
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The licence page on the television lists every licence by the setting that offers it, and the remote walks them in order. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvLicenceScreenFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun show() {
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            ArchiveTvTheme { TvLicenceScreen() }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    @Test
    fun `every group of the setting is on the page, not only the four defaults`() {
        show()

        listOf(
            R.string.licences_group_public_domain,
            R.string.licences_group_cc_by,
            R.string.licences_group_other_cc,
            R.string.licences_group_unclear,
        ).forEach { compose.onNodeWithText(text(it)).assertExists() }
        compose.onNodeWithText(text(R.string.licences_cc_by_sa)).assertExists()
        compose.onNodeWithText(text(R.string.licences_restricted)).assertExists()
    }

    @Test
    fun `the remote walks the licences in the phone's order`() {
        show()
        val first = compose.onNodeWithText(text(R.string.licence_public_domain))
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        first.assertIsFocused()

        first.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.licence_public_domain_us)).assertIsFocused()
    }
}
