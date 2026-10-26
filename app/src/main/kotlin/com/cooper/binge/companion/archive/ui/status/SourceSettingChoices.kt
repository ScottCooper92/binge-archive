package com.cooper.binge.companion.archive.ui.status

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.archive.R
import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.cooper.binge.companion.archive.settings.MinimumQuality

/** One choice of a Sources picker: the value it saves, its label and its icon. The phone and the television share these. */
data class SettingChoice<T>(
    val value: T,
    @param:StringRes val label: Int,
    val icon: ImageVector,
)

/** The Sources section's rows and choices, in the order they show. */
object SourceSettingChoices {
    val titleMatchesIcon: ImageVector = Icons.AutoMirrored.Outlined.ManageSearch
    val licencesIcon: ImageVector = Icons.Outlined.Policy
    val qualityIcon: ImageVector = Icons.Outlined.Videocam
    val subtitlesIcon: ImageVector = Icons.Outlined.ClosedCaption

    val licences: List<SettingChoice<LicenceLevel>> =
        listOf(
            SettingChoice(LicenceLevel.PUBLIC_DOMAIN, R.string.settings_licences_public_domain, Icons.Outlined.LockOpen),
            SettingChoice(LicenceLevel.CREATIVE_COMMONS, R.string.settings_licences_creative_commons, Icons.Outlined.Copyright),
            SettingChoice(LicenceLevel.ANY, R.string.settings_licences_any, Icons.Outlined.ReportProblem),
        )

    /** What each licence level means, shown under the slider. */
    val licenceExplanations: Map<LicenceLevel, Int> =
        mapOf(
            LicenceLevel.PUBLIC_DOMAIN to R.string.settings_licences_public_domain_explain,
            LicenceLevel.CREATIVE_COMMONS to R.string.settings_licences_creative_commons_explain,
            LicenceLevel.ANY to R.string.settings_licences_any_explain,
        )

    val qualities: List<SettingChoice<MinimumQuality>> =
        listOf(
            SettingChoice(MinimumQuality.ANY, R.string.settings_quality_any, Icons.Outlined.AllInclusive),
            SettingChoice(MinimumQuality.HD_720, R.string.settings_quality_720, Icons.Outlined.Hd),
            SettingChoice(MinimumQuality.HD_1080, R.string.settings_quality_1080, Icons.Outlined.HighQuality),
        )
}
