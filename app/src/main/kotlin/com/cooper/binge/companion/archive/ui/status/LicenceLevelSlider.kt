package com.cooper.binge.companion.archive.ui.status

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * confirm ([confirming]); the thumb stays on "Any" while that is asked and returns to the saved level on a cancel. Each stop's label is also a tap target.
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
    // While "Any" waits on its confirm, the thumb stays on it; a cancel returns it to the saved level.
    val shownIndex = if (confirming) choices.indexOfFirst { it.value == LicenceLevel.ANY } else savedIndex
    var position by remember(shownIndex) { mutableFloatStateOf(shownIndex.toFloat()) }
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
        StopLabels(selectedIndex = position.toInt(), accent = accent) { index ->
            position = index.toFloat()
            onSelect(choices[index].value)
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

/** The stops' icons and names: each icon centred on its tick, its name centred under it and held inside the row at the ends. */
@Composable
private fun StopLabels(
    selectedIndex: Int,
    accent: Color,
    onPick: (Int) -> Unit,
) {
    // Each stop's icon sits centred on its tick; its name is centred under the icon, held inside the row at the ends.
    Layout(
        content = {
            SourceSettingChoices.licences.forEachIndexed { index, choice ->
                val tint = if (index == selectedIndex) accent else MaterialTheme.colorScheme.onSurfaceVariant
                val select = { onPick(index) }
                Icon(
                    choice.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier =
                        Modifier
                            .clickable(onClick = select)
                            .clearAndSetSemantics { }
                            .padding(top = dimensionResource(DesR.dimen.padding_xs))
                            .size(20.dp),
                )
                Text(
                    text = stringResource(stopLabels[index]),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .clickable(onClick = select)
                            .clearAndSetSemantics { }
                            .padding(bottom = dimensionResource(DesR.dimen.padding_xs)),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val inset = STOP_INSET.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // `content` alternates an icon and its name, so even indexes are icons and odd ones are names.
        val stops = measurables.size / 2
        val step = (width - 2 * inset).toFloat() / (stops - 1)
        val icons = measurables.filterIndexed { i, _ -> i % 2 == 0 }.map { it.measure(loose) }
        // A name may use only the room its stop owns, so a long one wraps under its icon instead of running into
        // the next: half a step either side of its tick, and at the ends from the row's edge.
        val labels =
            measurables.filterIndexed { i, _ -> i % 2 == 1 }.mapIndexed { i, measurable ->
                val room = if (i == 0 || i == stops - 1) inset + step / 2 else step
                measurable.measure(loose.copy(maxWidth = room.toInt().coerceAtMost(width)))
            }
        val iconHeight = icons.maxOf { it.height }
        val height = iconHeight + labels.maxOf { it.height }
        layout(width, height) {
            icons.forEachIndexed { i, icon ->
                val centre = inset + (step * i).toInt()
                icon.placeRelative(centre - icon.width / 2, 0)
                val label = labels[i]
                val x = (centre - label.width / 2).coerceIn(0, (width - label.width).coerceAtLeast(0))
                label.placeRelative(x, iconHeight)
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

/** How far the slider draws its first and last tick in from its edges: half its thumb. */
private val STOP_INSET = 10.dp
