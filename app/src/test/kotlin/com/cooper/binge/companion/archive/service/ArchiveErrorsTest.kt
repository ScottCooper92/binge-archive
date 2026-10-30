package com.cooper.binge.companion.archive.service

import com.cooper.binge.companion.archive.resolve.ResolveTimeoutException
import io.grpc.Status
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ArchiveErrorsTest {
    private val errors =
        ArchiveErrors(
            object : UserMessages {
                override fun notOnArchive() = "Not on the Internet Archive"

                override fun tookTooLong() = "Took too long"

                override fun locale() = "en-GB"
            },
        )

    @Test
    fun `a lookup that ran past its own budget is unavailable and says so, apart from an unreachable server`() {
        val timed = errors.ofFailure(ResolveTimeoutException())
        val unreachable = errors.ofFailure(IOException("no route"))

        assertEquals(Status.Code.UNAVAILABLE, timed?.status?.code)
        assertEquals(ArchiveErrors.TOOK_TOO_LONG, timed?.status?.description)
        assertEquals(Status.Code.UNAVAILABLE, unreachable?.status?.code)
        assertEquals(ArchiveErrors.UNREACHABLE, unreachable?.status?.description)
    }
}
