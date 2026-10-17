package io.github.scottcooper92.binge.archive.ui.tv

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDeviceTest {
    private fun configuration(uiMode: Int) = Configuration().apply { this.uiMode = uiMode }

    @Test
    fun `a television's ui mode is a television`() {
        assertTrue(configuration(Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES).isTelevision())
    }

    @Test
    fun `a phone's ui mode is not`() {
        assertFalse(configuration(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES).isTelevision())
    }
}
