package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The seed the build actually packaged. A build made offline has none, and this test is skipped
 * then rather than failed: the seed is fetched live, and the fixtures elsewhere cover the code.
 */
@RunWith(RobolectricTestRunner::class)
class SeedAssetTest {
    @Test
    fun `the packaged seed parses and holds the issue's two films`() {
        val seed = ApplicationProvider.getApplicationContext<Context>().assets.readSeed()
        assumeTrue("This build has no seed index", seed != null)

        val films =
            checkNotNull(seed)
                .links
                .filter { it.isFilm }
                .map { it.tmdbId }
                .toSet()
        assertTrue(10331 in films)
        assertTrue(653 in films)
    }
}

@RunWith(RobolectricTestRunner::class)
class SeedReadTest {
    @Test
    fun `a build without a seed reads as no seed`() {
        assertNull(readSeed { throw java.io.FileNotFoundException("index/seed.tsv") })
    }

    @Test
    fun `a seed that does not parse reads as no seed`() {
        assertNull(readSeed { "not a seed".byteInputStream() })
    }

    @Test
    fun `a seed reads back as its snapshot`() {
        val text = buildString { SeedFormat.write(sampleSnapshot, this) }

        assertEquals(sampleSnapshot, readSeed { text.byteInputStream() })
    }
}

@RunWith(RobolectricTestRunner::class)
class IndexDatabaseTest {
    @Test
    fun `the index database opens empty`() =
        kotlinx.coroutines.test.runTest {
            val database = IndexDatabase.open(ApplicationProvider.getApplicationContext())
            try {
                assertEquals(0, database.indexDao().refreshCount())
            } finally {
                database.close()
            }
        }
}
