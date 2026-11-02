package com.cooper.binge.companion.archive.index

import java.io.File
import java.io.IOException

/**
 * The build's seed step, apart from the process around it so it can be tested. It runs [fetch], retrying a failure a few
 * times with a growing wait, since one bad answer from a live host should not decide a build, then writes the seed.
 *
 * Without a seed it says why on [err]. A build that does not need one goes on, and the app starts with an empty index; one that
 * does ([generate]'s `requireSeed`, the device smoke) fails, so the red names the cause and not a test three steps later.
 */
class SeedGenerator(
    private val fetch: () -> IndexSnapshot,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val out: (String) -> Unit = ::println,
    private val err: (String) -> Unit = System.err::println,
) {
    /** Writes the seed to [output] and returns the process's exit code: 0, or 1 when [requireSeed] and there is none. */
    fun generate(
        output: File,
        requireSeed: Boolean,
    ): Int {
        val failure = fetchWithRetries()
        val snapshot = failure.snapshot
        if (snapshot == null) {
            val reason = failure.reason
            return if (requireSeed) {
                err("error: no index seed was generated: $reason")
                1
            } else {
                err("warning: no index seed was generated ($reason). The app will start with an empty index.")
                0
            }
        }
        output.parentFile?.mkdirs()
        val temp = File(output.path + ".tmp")
        temp.bufferedWriter().use { SeedFormat.write(snapshot, it) }
        if (!temp.renameTo(output)) {
            temp.delete()
            err("error: could not move the seed into place at ${output.path}")
            return if (requireSeed) 1 else 0
        }
        out(
            "Index seed: ${snapshot.links.size} Wikidata links, ${snapshot.archiveItems.size} Archive items, ${snapshot.imdbLinks.size} IMDb links",
        )
        return 0
    }

    private class Fetched(
        val snapshot: IndexSnapshot?,
        val reason: String?,
    )

    private fun fetchWithRetries(): Fetched {
        var reason: String? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return Fetched(fetch(), null)
            } catch (e: IOException) {
                reason = e.message ?: e.javaClass.simpleName
                if (attempt < ATTEMPTS - 1) sleep(FIRST_WAIT_MILLIS shl attempt)
            }
        }
        return Fetched(null, reason)
    }

    private companion object {
        const val ATTEMPTS = 3
        const val FIRST_WAIT_MILLIS = 2_000L
    }
}
