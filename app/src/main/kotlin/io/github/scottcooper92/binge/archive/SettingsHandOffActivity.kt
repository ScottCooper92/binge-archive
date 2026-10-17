package io.github.scottcooper92.binge.archive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HandOffPolicy
import io.github.scottcooper92.binge.archive.service.ADMITS_UNVERIFIED_CALLERS

/**
 * The Activity behind Binge's "manage" row (`CompanionManifest.ACTION_SETTINGS`). Binge starts it for
 * a result; it takes no extras and answers none. It admits its caller and opens the status screen.
 *
 * Its own Activity, so it is the caller's activity-for-result and `callingPackage` is set: without
 * one, the policy refuses, which is the point. A start that is not for a result is not from a host.
 */
class SettingsHandOffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handOffAdmits(callingPackage)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    /** Release pins Binge's certificate, the same list the Service pins; debug admits Binge's package names. */
    private fun handOffAdmits(callingPackage: String?): Boolean =
        if (ADMITS_UNVERIFIED_CALLERS) {
            HandOffPolicy.anyCertificateOf(tag = TAG).permits(callingPackage)
        } else {
            HandOffPolicy.pinned(this, listOf(BingeHosts.release)).permits(callingPackage)
        }

    private companion object {
        const val TAG = "ArchiveHandOff"
    }
}
