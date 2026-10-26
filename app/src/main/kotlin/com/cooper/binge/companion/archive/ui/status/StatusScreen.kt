package com.cooper.binge.companion.archive.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.CenteredContent
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.bingeChoiceItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ErrorScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.archive.R
import java.text.NumberFormat
import com.binge.designsystem.R as DesR

/**
 * The phone's status screen: what this app is, the Sources settings, the index, who the data comes
 * from, and this device's recently resolved titles and reports. [now] is a parameter so frames pass a
 * fixed instant. On a window wider than the reading width the column is capped and centred.
 *
 * As the list pane of [StatusListDetail], [selected] names the detail open beside it: the Sources
 * section is then a row that opens it, and the open row is marked. Null is the screen on its own.
 */
@Composable
fun StatusScreen(
    state: StatusUiState,
    actions: StatusActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    selected: StatusDetail? = null,
) {
    BingeScreenScaffold(title = stringResource(R.string.app_label), modifier = modifier, bar = ScreenBar.Small) { padding ->
        ScreenBody(padding) { _ ->
            when (state) {
                StatusUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                StatusUiState.Error -> ErrorScreen(kind = ErrorKind.Generic, onRetry = actions.onRetry)
                is StatusUiState.Ready -> StatusGroups(state, actions, now, padding, selected)
            }
        }
    }
}

@Composable
private fun StatusGroups(
    state: StatusUiState.Ready,
    actions: StatusActions,
    now: Long,
    padding: androidx.compose.foundation.layout.PaddingValues,
    selected: StatusDetail?,
) {
    CenteredContent { capped ->
        LazyColumn(
            modifier = capped,
            contentPadding = padding.screenListPadding(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
        ) {
            item { ItemGroup(title = stringResource(R.string.status_about_title), rows = aboutRows(actions, selected)) }
            item {
                if (selected == null) {
                    SourceSettingsGroup(state, actions)
                } else {
                    ItemGroup(title = null, rows = listOf(sourcesRow(state, actions, selected)))
                }
            }
            item { ItemGroup(title = stringResource(R.string.status_index_title), rows = indexRows(state, actions, now)) }
            item { ItemGroup(title = stringResource(R.string.status_sources_title), rows = sourceRows(actions)) }
            item { ItemGroup(title = stringResource(R.string.status_recent_title), rows = recentRows(state.recent, actions, now)) }
            item { ItemGroup(title = stringResource(R.string.status_reported_title), rows = reportedRows(state.reported, actions)) }
        }
    }
}

/** In the list pane, the Sources section as one row that opens it beside the list, its detail the licences chosen. */
@Composable
private fun sourcesRow(
    state: StatusUiState.Ready,
    actions: StatusActions,
    selected: StatusDetail,
): ListItem =
    ListItem(
        icon = SourceSettingChoices.licencesIcon,
        label = stringResource(R.string.settings_title),
        detail = stringResource(SourceSettingChoices.licences.first { it.value == state.sources.licences }.label),
        supportingMaxLines = SETTING_DETAIL_LINES,
        selected = selected == StatusDetail.Sources,
        onClick = actions.onOpenSources,
    )

@Composable
private fun aboutRows(
    actions: StatusActions,
    selected: StatusDetail?,
): List<ListItem> =
    listOf(
        ListItem(
            icon = Icons.Outlined.Info,
            label = stringResource(R.string.status_about_headline),
            detail = stringResource(R.string.status_about_body),
            supportingMaxLines = ABOUT_LINES,
            clickable = false,
        ),
        ListItem(
            icon = Icons.Outlined.Gavel,
            label = stringResource(R.string.status_licences_row),
            detail = stringResource(R.string.status_licences_detail),
            supportingMaxLines = SETTING_DETAIL_LINES,
            selected = selected == StatusDetail.Licences,
            onClick = actions.onOpenLicences,
        ),
    )

/**
 * The Sources settings, saved as made. "Any, whatever the licence" asks first, on [AnyLicenceConfirm],
 * shown while [StatusUiState.Ready.confirmingAnyLicence].
 */
@Composable
fun SourceSettingsGroup(
    state: StatusUiState.Ready,
    actions: StatusActions,
    title: String? = stringResource(R.string.settings_title),
) {
    if (state.confirmingAnyLicence) {
        BingeConfirmDialog(
            title = stringResource(R.string.settings_any_title),
            message = stringResource(R.string.settings_any_message),
            confirmLabel = stringResource(R.string.settings_any_confirm),
            onConfirm = actions.onConfirmAnyLicence,
            onDismissRequest = actions.onCancelAnyLicence,
            destructive = true,
        )
    }
    ItemGroup(title = title, rows = sourceSettingRows(state, actions))
}

/** The confirm "Any, whatever the licence" asks, without its modal window, for a frame. */
@Composable
fun AnyLicenceConfirm(actions: StatusActions) {
    BingeConfirmDialogContent(
        title = stringResource(R.string.settings_any_title),
        message = stringResource(R.string.settings_any_message),
        confirmLabel = stringResource(R.string.settings_any_confirm),
        onConfirm = actions.onConfirmAnyLicence,
        onDismissRequest = actions.onCancelAnyLicence,
        destructive = true,
    )
}

@Composable
private fun sourceSettingRows(
    state: StatusUiState.Ready,
    actions: StatusActions,
): List<ListItem> {
    val settings = state.sources
    return listOf(
        ListItem(
            icon = SourceSettingChoices.titleMatchesIcon,
            label = stringResource(R.string.settings_title_matches),
            detail = stringResource(R.string.settings_title_matches_detail),
            supportingMaxLines = SETTING_DETAIL_LINES,
            toggled = settings.titleMatches,
            onClick = { actions.onTitleMatches(!settings.titleMatches) },
        ),
        bingeChoiceItem(
            icon = SourceSettingChoices.licencesIcon,
            title = stringResource(R.string.settings_licences),
            choices = SourceSettingChoices.licences.toChoiceList(),
            selected = settings.licences,
            emptyLabel = "",
            onSelect = actions.onLicences,
        ).copy(supportingMaxLines = SETTING_DETAIL_LINES),
        bingeChoiceItem(
            icon = SourceSettingChoices.qualityIcon,
            title = stringResource(R.string.settings_quality),
            choices = SourceSettingChoices.qualities.toChoiceList(),
            selected = settings.minimumQuality,
            emptyLabel = "",
            onSelect = actions.onMinimumQuality,
        ).copy(supportingMaxLines = SETTING_DETAIL_LINES),
        ListItem(
            icon = SourceSettingChoices.subtitlesIcon,
            label = stringResource(R.string.settings_subtitles),
            toggled = settings.subtitlesInMyLanguage,
            onClick = { actions.onSubtitlesInMyLanguage(!settings.subtitlesInMyLanguage) },
        ),
    )
}

@Composable
private fun <T> List<SettingChoice<T>>.toChoiceList(): BingeChoiceList<T> =
    BingeChoiceList.Ready(map { BingeChoice(it.value, stringResource(it.label), icon = it.icon) })

@Composable
private fun indexRows(
    state: StatusUiState.Ready,
    actions: StatusActions,
    now: Long,
): List<ListItem> =
    buildList {
        add(
            ListItem(
                icon = Icons.Outlined.Inventory2,
                label = stringResource(R.string.status_films_linked),
                detail = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(state.filmCount),
                clickable = false,
            ),
        )
        add(
            ListItem(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.status_last_refreshed),
                detail = formatRelativeOrAbsolute(state.lastRefreshedAt, now) ?: stringResource(R.string.status_never_refreshed),
                clickable = false,
            ),
        )
        if (state.lastRefreshFailed) {
            add(
                ListItem(
                    icon = Icons.Outlined.CloudOff,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.status_refresh_failed),
                    detail = stringResource(R.string.status_refresh_failed_detail),
                    detailColor = MaterialTheme.colorScheme.error,
                    supportingMaxLines = 2,
                    clickable = false,
                ),
            )
        }
        add(
            ListItem(
                icon = Icons.Outlined.Refresh,
                label = stringResource(R.string.status_refresh_now),
                detail = if (state.refreshing) stringResource(R.string.status_refresh_waiting) else null,
                destination = ListItemDestination.Action,
                loading = state.refreshing,
                onClick = actions.onRefreshNow,
            ),
        )
    }

@Composable
private fun sourceRows(actions: StatusActions): List<ListItem> =
    listOf(
        ListItem(
            icon = Icons.Outlined.Inventory2,
            label = stringResource(R.string.status_archive),
            detail = stringResource(R.string.status_archive_detail),
            destination = ListItemDestination.External,
            onClick = { actions.onOpenUrl(StatusLinks.ARCHIVE) },
        ),
        ListItem(
            icon = Icons.Outlined.Public,
            label = stringResource(R.string.status_wikidata),
            detail = stringResource(R.string.status_wikidata_detail),
            destination = ListItemDestination.External,
            onClick = { actions.onOpenUrl(StatusLinks.WIKIDATA) },
        ),
    )

@Composable
private fun recentRows(
    recent: List<RecentTitle>,
    actions: StatusActions,
    now: Long,
): List<ListItem> {
    if (recent.isEmpty()) {
        return listOf(ListItem(icon = Icons.Filled.Movie, label = stringResource(R.string.status_recent_empty), clickable = false))
    }
    return recent.map { title ->
        ListItem(
            icon = Icons.Filled.Movie,
            label = title.title,
            detail = formatRelativeOrAbsolute(title.resolvedAt, now),
            destination = ListItemDestination.External,
            onClick = { actions.onOpenUrl(StatusLinks.item(title.identifier)) },
        )
    } +
        ListItem(
            icon = Icons.Outlined.ClearAll,
            label = stringResource(R.string.status_recent_clear),
            destination = ListItemDestination.Action,
            onClick = actions.onClearRecent,
        )
}

@Composable
private fun reportedRows(
    reported: List<String>,
    actions: StatusActions,
): List<ListItem> {
    if (reported.isEmpty()) {
        return listOf(ListItem(icon = Icons.Outlined.Flag, label = stringResource(R.string.status_reported_empty), clickable = false))
    }
    return reported.map { identifier ->
        ListItem(
            icon = Icons.Outlined.Flag,
            label = identifier,
            detail = stringResource(R.string.status_reported_detail),
            destination = ListItemDestination.External,
            onClick = { actions.onOpenUrl(StatusLinks.item(identifier)) },
        )
    } +
        ListItem(
            icon = Icons.Outlined.ClearAll,
            label = stringResource(R.string.status_reported_clear),
            destination = ListItemDestination.Action,
            onClick = actions.onClearReported,
        )
}

/** Enough that the about text is never cut, even in a list pane at a large font. */
private const val ABOUT_LINES = 12

/** A setting's subtitle or value is one short line; room for more keeps it whole at a large font size. */
private const val SETTING_DETAIL_LINES = 3
