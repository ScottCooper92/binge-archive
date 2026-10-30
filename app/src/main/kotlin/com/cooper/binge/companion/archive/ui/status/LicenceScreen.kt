package com.cooper.binge.companion.archive.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.CenteredContent
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.archive.R
import com.binge.designsystem.R as DesR

/** The default licences, in plain words: what is offered unless the Sources settings say otherwise. */
@Composable
fun LicenceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(
        title = stringResource(R.string.licences_title),
        onBack = onBack,
        modifier = modifier,
        bar = ScreenBar.Small,
    ) { padding ->
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
                                        detail = stringResource(R.string.licences_intro_all),
                                        supportingMaxLines = INTRO_LINES,
                                        clickable = false,
                                    ),
                                ),
                        )
                    }
                    licenceGroups().forEach { (title, types) ->
                        item {
                            ItemGroup(
                                title = stringResource(title),
                                rows =
                                    types.map { type ->
                                        ListItem(
                                            icon = type.icon,
                                            label = stringResource(type.name),
                                            detail = stringResource(type.detail),
                                            supportingMaxLines = 2,
                                            clickable = false,
                                        )
                                    },
                            )
                        }
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

/** One licence type: its name, what it means, and its icon. */
private data class LicenceType(
    val name: Int,
    val detail: Int,
    val icon: ImageVector,
)

/** Every licence type, grouped by the Licences setting that first offers it, most open first. */
private fun licenceGroups(): List<Pair<Int, List<LicenceType>>> =
    listOf(
        R.string.licences_group_public_domain to
            listOf(
                LicenceType(R.string.licence_public_domain, R.string.licences_pdm_detail, Icons.Outlined.Public),
                LicenceType(R.string.licence_public_domain_us, R.string.licences_pd_us_detail, Icons.Outlined.Public),
                LicenceType(R.string.licence_cc0, R.string.licences_cc0_detail, Icons.Outlined.VolunteerActivism),
            ),
        R.string.licences_group_cc_by to
            listOf(LicenceType(R.string.licences_cc_by, R.string.licences_cc_by_detail, Icons.Outlined.Copyright)),
        R.string.licences_group_other_cc to
            listOf(
                LicenceType(R.string.licences_cc_by_sa, R.string.licences_cc_by_sa_detail, Icons.Outlined.Sync),
                LicenceType(R.string.licences_cc_by_nc, R.string.licences_cc_by_nc_detail, Icons.Outlined.MoneyOff),
                LicenceType(R.string.licences_cc_by_nd, R.string.licences_cc_by_nd_detail, Icons.Outlined.Lock),
                LicenceType(R.string.licences_cc_by_nc_sa, R.string.licences_cc_by_nc_sa_detail, Icons.Outlined.MoneyOff),
                LicenceType(R.string.licences_cc_by_nc_nd, R.string.licences_cc_by_nc_nd_detail, Icons.Outlined.MoneyOff),
            ),
        R.string.licences_group_unclear to
            listOf(
                LicenceType(R.string.licence_none_stated, R.string.licences_none_stated_detail, Icons.Outlined.TextSnippet),
                LicenceType(R.string.licences_restricted, R.string.licences_restricted_detail, Icons.Outlined.Block),
                LicenceType(R.string.licences_unknown, R.string.licences_unknown_detail, Icons.Outlined.HelpOutline),
            ),
    )

private const val INTRO_LINES = 4
