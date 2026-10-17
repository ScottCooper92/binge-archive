package io.github.scottcooper92.binge.archive.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.template.TvMessagePage
import io.github.scottcooper92.binge.archive.R

/** The television's counterpart of the phone's home screen, with the same words. */
@Composable
fun TvHomeScreen(modifier: Modifier = Modifier) {
    TvMessagePage(
        headline = stringResource(R.string.home_headline),
        body = stringResource(R.string.home_body),
        modifier = modifier,
    )
}
