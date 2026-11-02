package com.cooper.binge.companion.archive.ui.status

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.archive.R

/** One licence type: its name, what it means, and its icon. */
internal data class LicenceType(
    val name: Int,
    val detail: Int,
    val icon: ImageVector,
)

/** Every licence type, grouped by the Licences setting that first offers it, narrowest setting first. */
internal fun licenceGroups(): List<Pair<Int, List<LicenceType>>> =
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
