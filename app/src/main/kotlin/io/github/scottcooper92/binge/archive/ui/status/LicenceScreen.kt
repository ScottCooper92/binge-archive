package io.github.scottcooper92.binge.archive.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.CenteredContent
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import io.github.scottcooper92.binge.archive.R
import com.binge.designsystem.R as DesR

/** The default licences, in plain words: what is offered unless the Sources settings say otherwise. */
@Composable
fun LicenceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = stringResource(R.string.licences_title), onBack = onBack, modifier = modifier) { padding ->
        ScreenBody(padding) { _ ->
            CenteredContent { capped ->
                LazyColumn(
                    modifier = capped,
                    contentPadding = padding.screenListPadding(),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
                ) {
                    item {
                        ItemGroup(
                            title = null,
                            rows =
                                listOf(
                                    ListItem(
                                        icon = Icons.Outlined.Gavel,
                                        label = stringResource(R.string.licences_title),
                                        detail = stringResource(R.string.licences_intro),
                                        supportingMaxLines = INTRO_LINES,
                                        clickable = false,
                                    ),
                                ),
                        )
                    }
                    item { ItemGroup(title = stringResource(R.string.licences_allowed), rows = allowedRows()) }
                    item {
                        ItemGroup(
                            title = null,
                            rows =
                                listOf(
                                    ListItem(
                                        icon = Icons.Outlined.Block,
                                        label = stringResource(R.string.licences_not_allowed),
                                        supportingMaxLines = INTRO_LINES,
                                        clickable = false,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** The four allowed licences, each with its name and what it means. Shared with the TV page. */
@Composable
internal fun allowedLicences(): List<Pair<String, String>> =
    listOf(
        stringResource(R.string.licence_public_domain) to stringResource(R.string.licences_pdm_detail),
        stringResource(R.string.licence_public_domain_us) to stringResource(R.string.licences_pd_us_detail),
        stringResource(R.string.licence_cc0) to stringResource(R.string.licences_cc0_detail),
        stringResource(R.string.licences_cc_by) to stringResource(R.string.licences_cc_by_detail),
    )

@Composable
private fun allowedRows(): List<ListItem> {
    val icons = listOf(Icons.Outlined.Public, Icons.Outlined.Public, Icons.Outlined.VolunteerActivism, Icons.Outlined.Copyright)
    return allowedLicences().zip(icons) { (name, detail), icon ->
        ListItem(icon = icon, label = name, detail = detail, supportingMaxLines = 2, clickable = false)
    }
}

private const val INTRO_LINES = 4
