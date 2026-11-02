package com.cooper.binge.companion.archive.index

import java.io.File
import kotlin.system.exitProcess

/**
 * Writes the seed index to the file named by the first argument. The build runs this; see
 * `generateIndexSeed` in app/build.gradle.kts. The second argument is the app's version, for the User-Agent, and a third,
 * `require`, makes a missing seed fail the build.
 *
 * Offline, or with either host down, it writes nothing and exits cleanly unless a seed is required: the build goes on, and
 * the app starts with an empty index until its first refresh. The work is [SeedGenerator].
 */
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: exitProcess(2))
    val version = args.getOrNull(1) ?: "build"
    val requireSeed = args.getOrNull(2) == "require"
    exitProcess(SeedGenerator(fetch = { IndexFetcher(ArchiveHttp.client(version)).fetch() }).generate(output, requireSeed))
}
