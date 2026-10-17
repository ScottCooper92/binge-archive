package io.github.scottcooper92.binge.archive.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneSplit
import com.binge.designsystem.tv.template.TvTwoPaneStyle
import io.github.scottcooper92.binge.archive.R
import io.github.scottcooper92.binge.archive.ui.status.allowedLicences
import com.binge.designsystem.R as DesR

/** The licence allowlist on the television, the same words as the phone's page. Back returns to the status screen. */
@Composable
fun TvLicenceScreen(modifier: Modifier = Modifier) {
    val entry = remember { FocusRequester() }
    TvTwoPanePage(
        title = stringResource(R.string.licences_title),
        split = TvTwoPaneSplit.ListDetail,
        style = TvTwoPaneStyle.Board,
        entry = entry,
        modifier = modifier,
        copy = {
            TvTwoPaneCopy(
                headline = stringResource(R.string.licences_allowed),
                body = stringResource(R.string.licences_intro),
                note = stringResource(R.string.licences_not_allowed),
            )
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                allowedLicences().forEachIndexed { index, (name, detail) ->
                    TvOptionRow(
                        label = name,
                        selected = false,
                        onClick = {},
                        icon = Icons.Outlined.Gavel,
                        supportingText = detail,
                        modifier = if (index == 0) Modifier.focusRequester(entry) else Modifier,
                    )
                }
            }
        },
    )
}
