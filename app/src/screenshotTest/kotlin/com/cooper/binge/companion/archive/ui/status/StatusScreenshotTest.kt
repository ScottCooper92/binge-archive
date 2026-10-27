package com.cooper.binge.companion.archive.ui.status

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
import com.cooper.binge.companion.archive.preview.ArchiveFontScalePreviews
import com.cooper.binge.companion.archive.preview.ArchiveScreenPreviews
import com.cooper.binge.companion.archive.preview.ArchiveScreenStatePreview
import com.cooper.binge.companion.archive.preview.ArchiveSettingsFontScalePreviews
import com.cooper.binge.companion.archive.preview.ArchiveSpanishPreviews
import com.cooper.binge.companion.archive.preview.ArchiveWideStressPreviews
import com.binge.designsystem.R as DesR

/** The phone's status screen: its layout across the matrix, then each state on the phone cell alone. */
class StatusScreenshotTest {
    @PreviewTest
    @ArchiveScreenPreviews
    @ArchiveWideStressPreviews
    @Composable
    fun ready() = PhoneStatusRoot(previewReady, StatusActions(), showingLicences = false, onCloseLicences = {}, now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveFontScalePreviews
    @Composable
    fun readyFontScale() = PhoneStatusRoot(previewReady, StatusActions(), showingLicences = false, onCloseLicences = {}, now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun readySpanish() = PhoneStatusRoot(previewReady, StatusActions(), showingLicences = false, onCloseLicences = {}, now = PREVIEW_NOW)

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

    /** The slider on its warning stop, "Any" saved. */
    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun anyLicence() = SectionFrame { SourceSettingsGroup(previewAnyLicence, StatusActions()) }

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun anyLicenceSpanish() = SectionFrame { SourceSettingsGroup(previewAnyLicence, StatusActions()) }

    @PreviewTest
    @ArchiveSettingsFontScalePreviews
    @Composable
    fun anyLicenceFontScale() = SectionFrame { SourceSettingsGroup(previewAnyLicence, StatusActions()) }

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
    @ArchiveScreenPreviews
    @ArchiveWideStressPreviews
    @Composable
    fun licences() = PhoneStatusRoot(previewReady, StatusActions(), showingLicences = true, onCloseLicences = {}, now = PREVIEW_NOW)

    /** The Sources pane on its own with every setting moved, as the two-pane layout shows it. */
    @PreviewTest
    @ArchiveScreenStatePreview
    @ArchiveSpanishPreviews
    @Composable
    fun sourcesPane() = SourcesScreen(previewSourcesChanged, StatusActions())

    @PreviewTest
    @ArchiveScreenPreviews
    @Composable
    fun loadingWide() =
        PhoneStatusRoot(StatusUiState.Loading, StatusActions(), showingLicences = false, onCloseLicences = {
        }, now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun licencesSpanish() = LicenceScreen(onBack = {})

    /** The watch progress row: a count to clear, and nothing held. */
    @PreviewTest
    @ArchiveScreenStatePreview
    @ArchiveSettingsFontScalePreviews
    @Composable
    fun watchProgress() = SectionFrame { WatchProgressGroup(titles = 3, onClear = {}) }

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun watchProgressSpanish() = SectionFrame { WatchProgressGroup(titles = 3, onClear = {}) }

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun watchProgressEmpty() = SectionFrame { WatchProgressGroup(titles = 0, onClear = {}) }
}

/** A section or a dialog's card on the screen's background, scrolling so nothing is cut at a large font. */
@Composable
private fun SectionFrame(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.verticalScroll(rememberScrollState()).padding(dimensionResource(DesR.dimen.padding_m))) { content() }
    }
}
