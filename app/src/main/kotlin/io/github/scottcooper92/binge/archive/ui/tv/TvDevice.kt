package io.github.scottcooper92.binge.archive.ui.tv

import android.content.res.Configuration

/** Whether this configuration is a television's, which decides the surface MainActivity shows. */
internal fun Configuration.isTelevision(): Boolean = uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
