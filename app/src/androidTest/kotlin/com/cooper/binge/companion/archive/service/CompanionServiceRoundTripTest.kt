package com.cooper.binge.companion.archive.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.binge.companion.contracts.stream.v1.HandshakeRequest
import com.binge.companion.contracts.stream.v1.ProbeRequest
import com.binge.companion.contracts.stream.v1.ResolveRequest
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.v1.HostInfo
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderChannelBuilder
import io.grpc.binder.SecurityPolicies
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole path a host takes to this companion, across a real Binder on the minified build: the
 * exported Service under its caller policy, the generated stubs and the protobuf runtime under R8,
 * then handshake, probe and resolve for Night of the Living Dead (TMDB 10331).
 *
 * Probe answers from the packaged seed. Resolve goes to Wikidata and the Archive for real: the unit
 * tests cover the translation with the network faked, and this covers the crossing, so a red run
 * with an `UNAVAILABLE` names one of those two services rather than a keep rule.
 *
 * The debug policy admits Binge's certificates and the app's own uid, which is what lets the test bind
 * from inside the app; a release build's pinned policy would refuse it, so the lane runs the
 * minified debug build.
 */
@RunWith(AndroidJUnit4::class)
class CompanionServiceRoundTripTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun handshakeProbeAndResolveCrossTheBinderOnTheMinifiedBuild() =
        runBlocking {
            val channel =
                BinderChannelBuilder
                    .forAddress(AndroidComponentAddress.forRemoteComponent(app.packageName, SERVICE_CLASS), app)
                    .securityPolicy(SecurityPolicies.internalOnly())
                    .build()
            try {
                val stub = StreamServiceGrpcKt.StreamServiceCoroutineStub(channel)

                val handshake = withTimeout(CALL_TIMEOUT_MS) { stub.handshake(handshakeRequest()) }
                assertTrue("no capabilities declared: $handshake", handshake.capabilitiesCount > 0)
                assertTrue("no provider name declared", handshake.providerName.isNotBlank())

                val probe = withTimeout(CALL_TIMEOUT_MS) { stub.probe(ProbeRequest.newBuilder().setMedia(FILM).build()) }
                assertTrue("the packaged seed does not place the film", probe.mayHaveSources)

                val sources = withTimeout(RESOLVE_TIMEOUT_MS) { stub.resolve(ResolveRequest.newBuilder().setMedia(FILM).build()).toList() }
                assertTrue("no source for a public-domain film", sources.isNotEmpty())
                assertTrue(
                    "a source with a non-video type: ${sources.map { it.source.source.mimeType }}",
                    sources.all {
                        it.source.source.mimeType
                            .startsWith("video/")
                    },
                )
            } finally {
                channel.shutdownNow()
            }
        }

    private fun handshakeRequest(): HandshakeRequest =
        HandshakeRequest
            .newBuilder()
            .setHost(
                HostInfo
                    .newBuilder()
                    .setPackageName(app.packageName)
                    .setVersionName("androidTest")
                    .setVersionCode(1),
            ).build()

    private companion object {
        const val SERVICE_CLASS = "com.cooper.binge.companion.archive.service.ArchiveCompanionService"
        const val CALL_TIMEOUT_MS = 10_000L
        const val RESOLVE_TIMEOUT_MS = 60_000L
        val FILM: MediaId =
            MediaId
                .newBuilder()
                .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
                .setTmdbId(10331)
                .build()
    }
}
