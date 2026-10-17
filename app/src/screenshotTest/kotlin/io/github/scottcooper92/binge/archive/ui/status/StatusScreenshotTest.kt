package io.github.scottcooper92.binge.archive.ui.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.archive.preview.ArchiveFontScalePreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveScreenPreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveScreenStatePreview
import io.github.scottcooper92.binge.archive.preview.ArchiveSettingsFontScalePreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveSpanishPreviews
import com.binge.designsystem.R as DesR

/** The phone's status screen: its layout across the matrix, then each state on the phone cell alone. */
class StatusScreenshotTest {
    @PreviewTest
    @ArchiveScreenPreviews
    @Composable
    fun ready() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveFontScalePreviews
    @Composable
    fun readyFontScale() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun readySpanish() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun empty() = StatusScreen(previewEmpty, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun failedAndRefreshing() = StatusScreen(previewFailedRefreshing, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun refreshing() = StatusScreen(previewRefreshing, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun loading() = StatusScreen(StatusUiState.Loading, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun error() = StatusScreen(StatusUiState.Error, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun sourcesChanged() = StatusScreen(previewSourcesChanged, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun sourcesChangedSpanish() = StatusScreen(previewSourcesChanged, StatusActions(), now = PREVIEW_NOW)

    /** The Sources section alone, so a large font cannot push it out of the window. */
    @PreviewTest
    @ArchiveSettingsFontScalePreviews
    @Composable
    fun sourcesFontScale() = SectionFrame { SourceSettingsGroup(previewSourcesChanged, StatusActions()) }

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun anyLicenceConfirm() = SectionFrame { AnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun anyLicenceConfirmSpanish() = SectionFrame { AnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveSettingsFontScalePreviews
    @Composable
    fun anyLicenceConfirmFontScale() = SectionFrame { AnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun licences() = LicenceScreen(onBack = {})

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun licencesSpanish() = LicenceScreen(onBack = {})
}

/** A section or a dialog's card on the screen's background, scrolling so nothing is cut at a large font. */
@Composable
private fun SectionFrame(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.verticalScroll(rememberScrollState()).padding(dimensionResource(DesR.dimen.padding_m))) { content() }
    }
}
