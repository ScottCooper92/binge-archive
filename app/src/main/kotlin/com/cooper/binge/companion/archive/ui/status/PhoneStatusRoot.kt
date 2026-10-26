package com.cooper.binge.companion.archive.ui.status

import androidx.compose.runtime.Composable
import com.binge.designsystem.isExpandedLayout

/**
 * The phone's status UI, laid out for the window: the list and its detail side by side at 840dp and
 * wider, otherwise the status screen or the licence page alone. This is the one place the layout is
 * chosen; the screens below it never ask the window. [showingLicences] is the licence page open, or,
 * side by side, the licence page in the detail pane rather than Sources.
 */
@Composable
fun PhoneStatusRoot(
    state: StatusUiState,
    actions: StatusActions,
    showingLicences: Boolean,
    onCloseLicences: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    when {
        isExpandedLayout() ->
            StatusListDetail(state, actions, if (showingLicences) StatusDetail.Licences else StatusDetail.Sources, now = now)
        showingLicences -> LicenceScreen(onBack = onCloseLicences)
        else -> StatusScreen(state, actions, now = now)
    }
}
