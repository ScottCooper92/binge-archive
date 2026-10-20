package io.github.scottcooper92.binge.archive.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvNumberStepper
import com.binge.designsystem.tv.nav.tvContentGutterStart
import io.github.scottcooper92.binge.archive.R
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import io.github.scottcooper92.binge.archive.ui.status.SourceSettingChoices
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * The licence level on the television: one focus stop that ← and → step through, narrowest first, saved as each
 * press lands. Under it, what the level means; "Any" reads in the error colour as the warning.
 */
@Composable
fun TvLicenceSlider(
    selected: LicenceLevel,
    onSelect: (LicenceLevel) -> Unit,
) {
    val choices = SourceSettingChoices.licences
    val index = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val shown = choices[index]
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = tvContentGutterStart(),
                    end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                    top = dimensionResource(DesR.dimen.padding_m),
                    bottom = dimensionResource(DesR.dimen.padding_m),
                ),
    ) {
        Text(stringResource(R.string.settings_licences), style = MaterialTheme.typography.titleSmall)
        TvNumberStepper(
            value = index,
            onValueChange = { onSelect(choices[it].value) },
            range = choices.indices,
            valueLabel = stringResource(shown.label),
        )
        // Every level's copy shares one cell and only the chosen one is seen, so the rows below never move.
        Box {
            choices.forEach { choice ->
                TvLicenceExplanation(
                    level = choice.value,
                    modifier = if (choice == shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
                )
            }
        }
    }
}

@Composable
private fun TvLicenceExplanation(
    level: LicenceLevel,
    modifier: Modifier = Modifier,
) {
    val choice = SourceSettingChoices.licences.first { it.value == level }
    val color = if (level == LicenceLevel.ANY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(choice.icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        Text(
            text = stringResource(SourceSettingChoices.licenceExplanations.getValue(level)),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
