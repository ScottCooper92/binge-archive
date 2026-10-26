package com.cooper.binge.companion.archive.index

import android.content.res.AssetManager
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

private const val TAG = "IndexSeed"

/** Reads the seed the build put in the APK's assets. See [readSeed]. */
fun AssetManager.readSeed(): IndexSnapshot? = readSeed { open(SeedFormat.ASSET_PATH) }

/**
 * Reads a seed from [open]. A build made offline has none, and the app then starts with an empty
 * index until its first refresh; a seed that does not parse is treated the same way.
 */
internal fun readSeed(open: () -> InputStream): IndexSnapshot? =
    try {
        open().bufferedReader().use(SeedFormat::read)
    } catch (e: FileNotFoundException) {
        Log.i(TAG, "No seed index in this build", e)
        null
    } catch (e: IOException) {
        Log.w(TAG, "The seed index did not parse", e)
        null
    }
