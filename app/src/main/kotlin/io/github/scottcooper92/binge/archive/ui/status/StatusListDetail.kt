package io.github.scottcooper92.binge.archive.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import io.github.scottcooper92.binge.archive.R
import com.binge.designsystem.R as DesR

/** What the detail pane of [StatusListDetail] shows. */
enum class StatusDetail { Sources, Licences }

/**
 * The status screen on a window of two panes (840dp and wider), as the TV page splits it: the status
 * sections on the left, and on the right the [detail] the user opened, the Sources section by default.
 * The caller picks this or [StatusScreen] from the window's size, once, at the top.
 *
 * Until the state is ready there is nothing to put beside the list, so Loading and Error fill the window.
 */
@Composable
fun StatusListDetail(
    state: StatusUiState,
    actions: StatusActions,
    detail: StatusDetail,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    if (state !is StatusUiState.Ready) {
        StatusScreen(state, actions, modifier, now)
        return
    }
    CompositionLocalProvider(LocalIsSinglePaneNav provides false) {
        Row(modifier.fillMaxSize()) {
            PaneContent(Modifier.weight(LIST_WEIGHT), innerEdge = PaneEdge.End) {
                StatusScreen(state, actions, now = now, selected = detail)
            }
            PaneContent(Modifier.weight(DETAIL_WEIGHT), innerEdge = PaneEdge.Start) {
                when (detail) {
                    StatusDetail.Sources -> SourcesScreen(state, actions)
                    // The pane beside it is the way back, so the scaffold draws no Back.
                    StatusDetail.Licences -> LicenceScreen(onBack = actions.onOpenSources)
                }
            }
        }
    }
}

/** The Sources section as a detail pane of its own. */
@Composable
fun SourcesScreen(
    state: StatusUiState.Ready,
    actions: StatusActions,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = stringResource(R.string.settings_title), modifier = modifier) { padding ->
        ScreenBody(padding) { _ ->
            LazyColumn(
                contentPadding = padding.screenListPadding(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                item { SourceSettingsGroup(state, actions, title = null) }
            }
        }
    }
}

// Even halves: at the 840dp breakpoint the list pane is 420dp, a phone's width, so every row the
// phone keeps whole at a large font stays whole here.
private const val LIST_WEIGHT = 1f
private const val DETAIL_WEIGHT = 1f
