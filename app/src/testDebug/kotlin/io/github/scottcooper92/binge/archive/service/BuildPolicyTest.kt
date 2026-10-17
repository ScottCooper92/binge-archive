package io.github.scottcooper92.binge.archive.service

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPolicyTest {
    @Test
    fun `a debug build admits Binge's package names under any certificate`() {
        assertTrue(ADMITS_UNVERIFIED_CALLERS)
    }
}
