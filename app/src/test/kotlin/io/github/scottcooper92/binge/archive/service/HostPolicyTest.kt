package io.github.scottcooper92.binge.archive.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class HostPolicyTest {
    private val pinned = policy(Status.PERMISSION_DENIED)
    private val debug = policy(Status.OK)

    private fun policy(status: Status) =
        object : SecurityPolicy() {
            override fun checkAuthorization(uid: Int): Status = status
        }

    @Test
    fun `a release build selects the pinned policy, a debug build the package one`() {
        assertSame(pinned, selectHostPolicy(admitsUnverifiedCallers = false, pinned = { pinned }, debug = { debug }))
        assertSame(debug, selectHostPolicy(admitsUnverifiedCallers = true, pinned = { pinned }, debug = { debug }))
    }

    @Test
    fun `the debug policy admits this app's own uid and defers on any other`() {
        val self = SelfOrPolicy(selfUid = 10_001, policy = pinned)

        assertEquals(Status.OK, self.checkAuthorization(10_001))
        assertEquals(Status.PERMISSION_DENIED, self.checkAuthorization(10_002))
    }

    @Test
    fun `the Service is exported under the STREAM action with its meta-data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val services =
            context.packageManager.queryIntentServices(
                Intent("com.binge.companion.STREAM").setPackage(context.packageName),
                PackageManager.GET_META_DATA,
            )

        val service = services.single().serviceInfo
        assertEquals(ArchiveCompanionService::class.java.name, service.name)
        assertEquals(true, service.exported)
        assertEquals(1, service.metaData.getInt("com.binge.companion.majors"))
    }

    @Test
    fun `the user message comes from resources, in the device's locale`() {
        val messages = AndroidUserMessages(ApplicationProvider.getApplicationContext())

        assertEquals("Not on the Internet Archive", messages.notOnArchive())
        assertEquals(Locale.getDefault().toLanguageTag(), messages.locale())
    }
}
