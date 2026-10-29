package com.cooper.binge.companion.archive.resolve

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text cleanup on Android's own regex engine. The JVM unit tests cannot catch a pattern that only ICU rejects:
 * `(?U)\s` passed every one of them and failed every lookup on a device.
 */
@RunWith(AndroidJUnit4::class)
class SourceStringsDeviceTest {
    @Test
    fun whitespaceRunsBecomeOneSpaceOnAndroid() {
        assertEquals("Night of the dead", "Night of　the\u0085\u000B\n\tdead".plainText())
        assertEquals("a b", " a ​ b ".plainText())
    }
}
