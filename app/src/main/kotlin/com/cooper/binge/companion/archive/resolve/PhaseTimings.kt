package com.cooper.binge.companion.archive.resolve

import android.util.Log

/** Where a phase's duration goes. Only ever the device, like [CoverageLog]. */
fun interface PhaseLog {
    fun phase(
        name: String,
        elapsedMillis: Long,
    )
}

/** Writes each phase to logcat under the `Coverage` tag, for measuring a cold `Resolve`. */
object LogcatPhaseLog : PhaseLog {
    override fun phase(
        name: String,
        elapsedMillis: Long,
    ) {
        Log.i("Coverage", "phase $name ${elapsedMillis}ms")
    }
}

/**
 * Times the phases of a `Resolve`, for the first one after a cold start, which has to fit the host's 10 s
 * deadline. A phase is named by what it does, never by the title or a URL.
 */
class PhaseTimings(
    private val log: PhaseLog,
    private val clock: () -> Long = System::nanoTime,
) {
    /** Runs [block] and logs how long it took, whether it returned or threw. */
    suspend fun <T> time(
        name: String,
        block: suspend () -> T,
    ): T {
        val start = clock()
        try {
            return block()
        } finally {
            log.phase(name, millisSince(start))
        }
    }

    /** The mark [since] measures from. */
    fun mark(): Long = clock()

    /** Logs the time from [mark] to now as [name]. */
    fun since(
        name: String,
        mark: Long,
    ) = log.phase(name, millisSince(mark))

    private fun millisSince(mark: Long): Long = (clock() - mark) / NANOS_PER_MILLI

    companion object {
        /** Times nothing: for a resolver built without measurement. */
        val NONE = PhaseTimings(log = { _, _ -> })

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
