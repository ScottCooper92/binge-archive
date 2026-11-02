package com.cooper.binge.companion.archive.ui.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeStop
import com.binge.designsystem.component.BingeStopEmphasis
import com.binge.designsystem.component.BingeStopSlider
import com.cooper.binge.companion.archive.R
import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.binge.designsystem.R as DesR

/** The short names under the slider's four stops, in [SourceSettingChoices.licences] order. */
private val stopLabels =
    listOf(
        R.string.settings_licences_stop_public_domain,
        R.string.settings_licences_stop_default,
        R.string.settings_licences_stop_creative_commons,
        R.string.settings_licences_stop_any,
    )

/**
 * The licence level as a four-stop slider, narrowest on the left. Under it, the level chosen and what it means,
 * in a cell sized to the longest level so nothing moves as it changes. The explanation follows the thumb while it
 * is dragged, and the level is offered once it lands: every level but "Any" is saved at once, and "Any" waits on its
 * confirm ([confirming]); the thumb stays on "Any" while that is asked and returns to the saved level on a cancel.
 */
@Composable
fun LicenceLevelSlider(
    selected: LicenceLevel,
    confirming: Boolean,
    onSelect: (LicenceLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = SourceSettingChoices.licences
    val savedIndex = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val anyIndex = choices.indexOfFirst { it.value == LicenceLevel.ANY }
    val shownIndex = if (confirming) anyIndex else savedIndex
    // The explanation follows the thumb, and goes back to where the slider rests when that changes.
    var position by remember(shownIndex) { mutableIntStateOf(shownIndex) }
    val stops =
        choices.mapIndexed { index, choice ->
            BingeStop(
                label = stringResource(stopLabels[index]),
                icon = choice.icon,
                emphasis = if (choice.value == LicenceLevel.ANY) BingeStopEmphasis.Warning else BingeStopEmphasis.Normal,
            )
        }
    val shown = choices[position.coerceIn(choices.indices)]

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        BingeStopSlider(
            stops = stops,
            selectedIndex = savedIndex,
            holdIndex = if (confirming) anyIndex else null,
            onCommit = { index -> onSelect(choices[index].value) },
            onPositionChange = { position = it },
            description = stringResource(R.string.settings_licences),
        )
        Spacer(Modifier.size(dimensionResource(DesR.dimen.padding_m)))
        // Every level's copy is laid out in the same cell and only the chosen one is seen, so the cell is always as
        // tall as the longest: the card keeps its height, and nothing moves, as the level changes.
        Box {
            choices.forEach { choice ->
                LicenceExplanation(
                    level = choice.value,
                    modifier = if (choice == shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
                )
            }
        }
    }
}

/** A level's icon and name, and what it means under them. "Any" reads in the error colour as the warning. */
@Composable
private fun LicenceExplanation(
    level: LicenceLevel,
    modifier: Modifier = Modifier,
) {
    val choice = SourceSettingChoices.licences.first { it.value == level }
    val warning = level == LicenceLevel.ANY
    val accent = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(choice.icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_m)))
            Text(
                stringResource(choice.label),
                style = MaterialTheme.typography.titleMedium,
                color = if (warning) accent else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(dimensionResource(DesR.dimen.padding_s)))
        Text(
            text = stringResource(SourceSettingChoices.licenceExplanations.getValue(level)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
