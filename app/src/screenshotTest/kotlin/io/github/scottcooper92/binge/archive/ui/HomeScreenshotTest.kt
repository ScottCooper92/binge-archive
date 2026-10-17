package io.github.scottcooper92.binge.archive.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.archive.preview.ArchiveFontScalePreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveScreenPreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveTvScreenPreviews
import io.github.scottcooper92.binge.archive.ui.tv.TvHomeScreen

/** The home screen across the phone matrix and the larger font scales, and its television counterpart. */
class HomeScreenshotTest {
    @PreviewTest
    @ArchiveScreenPreviews
    @Composable
    fun home() = HomeScreen()

    @PreviewTest
    @ArchiveFontScalePreviews
    @Composable
    fun homeFontScale() = HomeScreen()

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun tvHome() = TvHomeScreen()
}
