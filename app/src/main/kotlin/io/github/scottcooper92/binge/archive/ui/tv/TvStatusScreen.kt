package io.github.scottcooper92.binge.archive.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.TvCheckboxRow
import com.binge.designsystem.tv.component.TvOptionGroup
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvSectionTitle
import com.binge.designsystem.tv.template.TvErrorPage
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneSplit
import com.binge.designsystem.tv.template.TvTwoPaneStyle
import io.github.scottcooper92.binge.archive.R
import io.github.scottcooper92.binge.archive.ui.status.SettingChoice
import io.github.scottcooper92.binge.archive.ui.status.SourceSettingChoices
import io.github.scottcooper92.binge.archive.ui.status.StatusActions
import io.github.scottcooper92.binge.archive.ui.status.StatusUiState
import java.text.NumberFormat
import com.binge.designsystem.R as DesR

/**
 * The television's status screen: the same sections as the phone's, in two panes. The Sources settings
 * apply in place as the user presses OK; "Any, whatever the licence" asks first, focus on Cancel. A television
 * usually has no browser, so the source and title rows say where they point rather than opening it.
 */
@Composable
fun TvStatusScreen(
    state: StatusUiState,
    actions: StatusActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    when (state) {
        StatusUiState.Loading -> TvMessagePage(body = stringResource(R.string.status_loading), loading = true, modifier = modifier)
        StatusUiState.Error -> TvErrorPage(kind = ErrorKind.Generic, onRetry = actions.onRetry, modifier = modifier)
        is StatusUiState.Ready -> {
            val entry = remember { FocusRequester() }
            TvTwoPanePage(
                title = stringResource(R.string.app_label),
                split = TvTwoPaneSplit.ListDetail,
                style = TvTwoPaneStyle.Board,
                entry = entry,
                modifier = modifier,
                copy = {
                    TvTwoPaneCopy(
                        headline = stringResource(R.string.status_about_headline),
                        body = stringResource(R.string.status_about_body),
                    )
                },
                action = { TvStatusRows(state, actions, now, entry) },
            )
        }
    }
}

@Composable
private fun ColumnScope.TvStatusRows(
    state: StatusUiState.Ready,
    actions: StatusActions,
    now: Long,
    entry: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        TvRow(
            label = stringResource(R.string.status_licences_row),
            supporting = stringResource(R.string.status_licences_detail),
            icon = Icons.Outlined.Gavel,
            onClick = actions.onOpenLicences,
            modifier = Modifier.focusRequester(entry),
        )

        TvSourceSettings(state, actions)

        TvSectionTitle(stringResource(R.string.status_index_title))
        TvRow(
            stringResource(R.string.status_films_linked),
            NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(state.filmCount),
            Icons.Outlined.Inventory2,
        )
        TvRow(
            stringResource(R.string.status_last_refreshed),
            formatRelativeOrAbsolute(state.lastRefreshedAt, now) ?: stringResource(R.string.status_never_refreshed),
            Icons.Outlined.Schedule,
        )
        if (state.lastRefreshFailed) {
            TvRow(
                stringResource(R.string.status_refresh_failed),
                stringResource(R.string.status_refresh_failed_detail),
                Icons.Outlined.CloudOff,
            )
        }
        TvRow(
            label = stringResource(R.string.status_refresh_now),
            supporting = if (state.refreshing) stringResource(R.string.status_refresh_waiting) else null,
            icon = Icons.Outlined.Refresh,
            onClick = actions.onRefreshNow,
            saving = state.refreshing,
        )

        TvSectionTitle(stringResource(R.string.status_sources_title))
        TvRow(stringResource(R.string.status_archive), stringResource(R.string.status_archive_detail), Icons.Outlined.Inventory2)
        TvRow(stringResource(R.string.status_wikidata), stringResource(R.string.status_wikidata_detail), Icons.Outlined.Public)

        TvSectionTitle(stringResource(R.string.status_recent_title))
        if (state.recent.isEmpty()) {
            TvRow(stringResource(R.string.status_recent_empty), null, Icons.Filled.Movie)
        } else {
            state.recent.forEach { TvRow(it.title, formatRelativeOrAbsolute(it.resolvedAt, now), Icons.Filled.Movie) }
            TvRow(stringResource(R.string.status_recent_clear), null, Icons.Outlined.ClearAll, onClick = actions.onClearRecent)
        }

        TvSectionTitle(stringResource(R.string.status_reported_title))
        if (state.reported.isEmpty()) {
            TvRow(stringResource(R.string.status_reported_empty), null, Icons.Outlined.Flag)
        } else {
            state.reported.forEach { TvRow(it, stringResource(R.string.status_reported_detail), Icons.Outlined.Flag) }
            TvRow(stringResource(R.string.status_reported_clear), null, Icons.Outlined.ClearAll, onClick = actions.onClearReported)
        }
    }
}

/** The Sources settings on the television: a row or a group per setting, each applying as OK is pressed. */
@Composable
fun TvSourceSettings(
    state: StatusUiState.Ready,
    actions: StatusActions,
) {
    val settings = state.sources
    TvSectionTitle(stringResource(R.string.settings_title))
    TvCheckboxRow(
        label = stringResource(R.string.settings_title_matches),
        checked = settings.titleMatches,
        onCheckedChange = actions.onTitleMatches,
        icon = SourceSettingChoices.titleMatchesIcon,
        supportingText = stringResource(R.string.settings_title_matches_detail),
    )
    TvLicenceSlider(selected = settings.licences, onSelect = actions.onLicences)
    TvSettingGroup(
        title = stringResource(R.string.settings_quality),
        choices = SourceSettingChoices.qualities,
        selected = settings.minimumQuality,
        onSelect = actions.onMinimumQuality,
    )
    TvCheckboxRow(
        label = stringResource(R.string.settings_subtitles),
        checked = settings.subtitlesInMyLanguage,
        onCheckedChange = actions.onSubtitlesInMyLanguage,
        icon = SourceSettingChoices.subtitlesIcon,
    )
}

@Composable
private fun <T> TvSettingGroup(
    title: String,
    choices: List<SettingChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    TvOptionGroup(
        title = title,
        choices = choices.map { it.value to stringResource(it.label) },
        selected = selected,
        onSelect = onSelect,
        // The group sizes itself to a text field; the rows above it span the column, so it does too.
        modifier = Modifier.fillMaxWidth(),
        choiceIcon = { value -> choices.first { it.value == value }.icon },
    )
}

/** One focusable row. A row with nothing to do still takes focus, so the remote can read it. */
@Composable
private fun TvRow(
    label: String,
    supporting: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    saving: Boolean = false,
) {
    TvOptionRow(
        label = label,
        selected = false,
        onClick = onClick,
        icon = icon,
        supportingText = supporting,
        saving = saving,
        modifier = modifier,
    )
}
