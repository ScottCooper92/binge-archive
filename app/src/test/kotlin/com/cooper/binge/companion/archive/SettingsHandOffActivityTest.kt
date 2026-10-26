package com.cooper.binge.companion.archive

import android.content.Intent
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.CompanionManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Binge's "manage" row. The action is the whole contract, so it is checked against the SDK's constant. */
@RunWith(RobolectricTestRunner::class)
class SettingsHandOffActivityTest {
    @Test
    fun `the manifest exports the hand-off on the action the contract names`() {
        val context = RuntimeEnvironment.getApplication()
        val resolved =
            context.packageManager
                .queryIntentActivities(Intent(CompanionManifest.ACTION_SETTINGS).setPackage(context.packageName), 0)
                .single()
                .activityInfo

        assertEquals(SettingsHandOffActivity::class.java.name, resolved.name)
        assertTrue(resolved.exported)
    }

    @Test
    fun `Binge, having started it for a result, is handed on to the status screen`() {
        val controller = Robolectric.buildActivity(SettingsHandOffActivity::class.java)
        shadowOf(controller.get()).setCallingPackage(BingeHosts.DEBUG_PACKAGE_NAME)

        controller.create()

        assertEquals(MainActivity::class.java.name, shadowOf(controller.get()).nextStartedActivity.component?.className)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `another app opens nothing`() {
        val controller = Robolectric.buildActivity(SettingsHandOffActivity::class.java)
        shadowOf(controller.get()).setCallingPackage("com.example.other")

        controller.create()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `a start with no calling package opens nothing`() {
        val controller = Robolectric.buildActivity(SettingsHandOffActivity::class.java)

        controller.create()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }
}
