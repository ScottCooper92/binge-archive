package io.github.scottcooper92.binge.archive.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.MessageScreen
import io.github.scottcooper92.binge.archive.R

/**
 * The phone's only screen for now: what the app is, and that it returns only openly licensed films.
 * The app has no catalogue to browse; films are found from Binge.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    MessageScreen(
        headline = stringResource(R.string.home_headline),
        body = stringResource(R.string.home_body),
        modifier = modifier,
    )
}
