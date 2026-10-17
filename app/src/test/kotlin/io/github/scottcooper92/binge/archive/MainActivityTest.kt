package io.github.scottcooper92.binge.archive

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher opens the surface the device asks for, and both say what the app returns. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `a phone opens on the home screen`() {
        compose.onNodeWithText(compose.activity.getString(R.string.home_headline)).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "television")
class MainActivityTelevisionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `a television opens on the TV home screen`() {
        compose.onNodeWithText(compose.activity.getString(R.string.home_headline)).assertExists()
    }
}
