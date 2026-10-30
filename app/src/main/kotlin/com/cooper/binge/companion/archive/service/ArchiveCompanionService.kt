package com.cooper.binge.companion.archive.service

import android.content.Context
import android.os.Process
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HostPolicy
import com.binge.companion.sdk.IntegrationService
import com.cooper.binge.companion.archive.ArchiveApp
import com.cooper.binge.companion.archive.BuildConfig
import com.cooper.binge.companion.archive.R
import io.grpc.BindableService
import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import java.util.Locale

/**
 * The exported Service Binge binds to. Hosting gRPC on Binder is the SDK's; what is this app's is the
 * contract it serves and who it lets in.
 */
class ArchiveCompanionService : IntegrationService() {
    override fun services(): List<BindableService> {
        val graph = (application as ArchiveApp).graph
        return listOf(
            ArchiveStreamService(
                resolver = graph.resolver,
                index = graph.index,
                records = graph.records,
                progress = graph.progress,
                versionName = BuildConfig.VERSION_NAME,
                errors = ArchiveErrors(AndroidUserMessages(this)),
            ),
        )
    }

    override fun hostPolicy(): SecurityPolicy =
        selectHostPolicy(
            admitsUnverifiedCallers = ADMITS_UNVERIFIED_CALLERS,
            pinned = { HostPolicy.pinned(this, listOf(BingeHosts.release)) },
            debug = { SelfOrPolicy(selfUid = Process.myUid(), policy = HostPolicy.anyCertificateOf(this, tag = TAG)) },
        )

    private companion object {
        const val TAG = "ArchiveCompanion"
    }
}

/**
 * Whether this build admits callers without verifying their certificate: true only on a debug build,
 * read from [BuildConfig.DEBUG] and never from anything a user can switch.
 */
internal val ADMITS_UNVERIFIED_CALLERS: Boolean = BuildConfig.DEBUG

/**
 * Release pins Binge's published certificate (`BingeHosts.release`) and refuses any other signer. Debug
 * admits Binge's package names under any certificate, since a debug Binge is signed with its developer's
 * own key, plus this app's own uid, so an on-device test can bind from inside the app.
 */
internal fun selectHostPolicy(
    admitsUnverifiedCallers: Boolean,
    pinned: () -> SecurityPolicy,
    debug: () -> SecurityPolicy,
): SecurityPolicy = if (admitsUnverifiedCallers) debug() else pinned()

/** [policy], plus this app's own uid. That uid is this app, so no other app gets in by it. */
internal class SelfOrPolicy(
    private val selfUid: Int,
    private val policy: SecurityPolicy,
) : SecurityPolicy() {
    override fun checkAuthorization(uid: Int): Status = if (uid == selfUid) Status.OK else policy.checkAuthorization(uid)
}

/** [UserMessages] from this app's resources, in the device's language. */
class AndroidUserMessages(
    private val context: Context,
) : UserMessages {
    override fun notOnArchive(): String = context.getString(R.string.not_on_archive)

    override fun tookTooLong(): String = context.getString(R.string.lookup_took_too_long)

    override fun locale(): String =
        context.resources.configuration.locales
            .get(0)
            ?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
}
