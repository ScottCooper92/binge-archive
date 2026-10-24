package io.github.scottcooper92.binge.archive.ui.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.scottcooper92.binge.archive.R
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import com.binge.designsystem.R as DesR

/** The short names under the slider's three stops, in [SourceSettingChoices.licences] order. */
private val stopLabels =
    listOf(
        R.string.settings_licences_stop_public_domain,
        R.string.settings_licences_stop_open,
        R.string.settings_licences_stop_any,
    )

/**
 * The licence level as a three-stop slider, narrowest on the left. Under it, the level chosen and what it means,
 * in a cell sized to the longest level so nothing moves as it changes. The explanation follows the thumb while it is dragged, and the
 * level is saved once it lands. Each stop's label is also a tap target.
 */
@Composable
fun LicenceLevelSlider(
    selected: LicenceLevel,
    onSelect: (LicenceLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = SourceSettingChoices.licences
    val savedIndex = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    var position by remember(savedIndex) { mutableFloatStateOf(savedIndex.toFloat()) }
    val shown = choices[position.toInt().coerceIn(choices.indices)]
    val warning = shown.value == LicenceLevel.ANY
    val accent = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val label = stringResource(shown.label)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onSelect(choices[position.toInt().coerceIn(choices.indices)].value) },
            valueRange = 0f..choices.lastIndex.toFloat(),
            steps = choices.size - 2,
            colors =
                SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    activeTickColor = MaterialTheme.colorScheme.contentColorFor(accent),
                    inactiveTrackColor = accent.copy(alpha = INACTIVE_TRACK_ALPHA),
                    inactiveTickColor = accent,
                ),
            modifier = Modifier.semantics { stateDescription = label },
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, choice ->
                val align =
                    when (index) {
                        0 -> Alignment.Start
                        choices.lastIndex -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                val on = index == position.toInt()
                val tint =
                    when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> accent
                    }
                Column(
                    horizontalAlignment = align,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                position = index.toFloat()
                                onSelect(choice.value)
                            }.clearAndSetSemantics { }
                            .padding(vertical = dimensionResource(DesR.dimen.padding_xs)),
                ) {
                    Icon(choice.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(stopLabels[index]),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        textAlign =
                            when (index) {
                                0 -> TextAlign.Start
                                choices.lastIndex -> TextAlign.End
                                else -> TextAlign.Center
                            },
                    )
                }
            }
        }
        Spacer(Modifier.size(dimensionResource(DesR.dimen.padding_m)))
        // Every level's copy is laid out in the same cell and only the chosen one is seen, so the cell is always as
        // tall as the longest: the card keeps its height, and nothing moves, as the level changes.
        Box {
            choices.forEach { choice ->
                val visible = choice == shown
                LicenceExplanation(
                    level = choice.value,
                    modifier = if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
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

/** The unfilled part of the track, a tint of the filled part as Material draws it. */
private const val INACTIVE_TRACK_ALPHA = 0.24f
