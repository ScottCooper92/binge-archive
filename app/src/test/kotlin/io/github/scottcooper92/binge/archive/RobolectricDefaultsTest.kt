package io.github.scottcooper92.binge.archive

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deliberately carries no `@Config`: it is the assertion that `robolectric.properties` supplies one.
 * Without that file a test falls back to `targetSdk` 36, whose sandbox requires Java 21.
 */
@RunWith(RobolectricTestRunner::class)
class RobolectricDefaultsTest {
    @Test
    fun `a test without @Config runs on the SDK this module's JDK can sandbox`() {
        assertEquals(34, Build.VERSION.SDK_INT)
    }
}
