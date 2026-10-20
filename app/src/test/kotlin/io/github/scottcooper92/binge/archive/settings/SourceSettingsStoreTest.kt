package io.github.scottcooper92.binge.archive.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.binge.companion.contracts.stream.v1.Resolution
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("settings.preferences_pb") } }
    private val store by lazy { SourceSettingsStore(dataStore) }

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun `a store never written reads as today's behaviour`() =
        runBlocking {
            val settings = store.current()

            assertEquals(SourceSettings.DEFAULT, settings)
            assertTrue(settings.titleMatches)
            assertEquals(LicenceLevel.CREATIVE_COMMONS, settings.licences)
            assertEquals(MinimumQuality.ANY, settings.minimumQuality)
            assertFalse(settings.subtitlesInMyLanguage)
        }

    @Test
    fun `each setting is saved as it is made`() =
        runBlocking {
            store.setTitleMatches(false)
            store.setLicences(LicenceLevel.ANY)
            store.setMinimumQuality(MinimumQuality.HD_1080)
            store.setSubtitlesInMyLanguage(true)

            assertEquals(
                SourceSettings(
                    titleMatches = false,
                    licences = LicenceLevel.ANY,
                    minimumQuality = MinimumQuality.HD_1080,
                    subtitlesInMyLanguage = true,
                ),
                store.settings.first(),
            )
        }

    @Test
    fun `choosing a default again stores nothing for it`() =
        runBlocking {
            store.setLicences(LicenceLevel.PUBLIC_DOMAIN)
            store.setMinimumQuality(MinimumQuality.HD_720)
            store.setLicences(LicenceLevel.DEFAULT)
            store.setMinimumQuality(MinimumQuality.ANY)

            assertEquals(SourceSettings.DEFAULT, store.current())
            assertTrue(
                dataStore.data
                    .first()
                    .asMap()
                    .isEmpty(),
            )
        }

    @Test
    fun `a value this build does not know reads as the default`() =
        runBlocking {
            dataStore.edit {
                it[stringPreferencesKey("licences")] = "EVERYTHING"
                it[stringPreferencesKey("minimum_quality")] = "HD_4320"
            }

            assertEquals(SourceSettings.DEFAULT, store.current())
        }

    @Test
    fun `the retired public domain and CC BY level reads as the default`() =
        runBlocking {
            dataStore.edit { it[stringPreferencesKey("licences")] = "PUBLIC_DOMAIN_AND_CC_BY" }

            assertEquals(LicenceLevel.DEFAULT, store.current().licences)
        }

    @Test
    fun `a minimum quality admits its class and those above it`() {
        assertEquals(
            listOf(true, true, true, true),
            listOf(
                Resolution.RESOLUTION_UNSPECIFIED,
                Resolution.RESOLUTION_SD,
                Resolution.RESOLUTION_HD_720,
                Resolution.RESOLUTION_UHD_2160,
            ).map(MinimumQuality.ANY::admits),
        )
        assertEquals(
            listOf(false, true, true),
            listOf(
                Resolution.RESOLUTION_SD,
                Resolution.RESOLUTION_HD_720,
                Resolution.RESOLUTION_HD_1080,
            ).map(MinimumQuality.HD_720::admits),
        )
        assertEquals(
            listOf(false, true, true),
            listOf(
                Resolution.RESOLUTION_HD_720,
                Resolution.RESOLUTION_HD_1080,
                Resolution.RESOLUTION_UHD_2160,
            ).map(MinimumQuality.HD_1080::admits),
        )
    }
}
