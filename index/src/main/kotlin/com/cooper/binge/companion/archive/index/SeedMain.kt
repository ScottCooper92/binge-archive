package com.cooper.binge.companion.archive.index

import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Writes the seed index to the file named by the first argument. The build runs this; see
 * `generateIndexSeed` in app/build.gradle.kts.
 *
 * Offline, or with either host down, it writes nothing and exits cleanly: the build goes on, and
 * the app starts with an empty index until its first refresh.
 */
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: exitProcess(2))
    val version = args.getOrNull(1) ?: "build"
    try {
        val snapshot = IndexFetcher(ArchiveHttp.client(version)).fetch()
        output.parentFile?.mkdirs()
        val temp = File(output.path + ".tmp")
        temp.bufferedWriter().use { SeedFormat.write(snapshot, it) }
        if (!temp.renameTo(output)) throw IOException("Could not move the seed into place")
        println(
            "Index seed: ${snapshot.links.size} Wikidata links, ${snapshot.archiveItems.size} Archive items, ${snapshot.imdbLinks.size} IMDb links",
        )
    } catch (e: IOException) {
        System.err.println("warning: no index seed was generated (${e.message}). The app will start with an empty index.")
    }
}
