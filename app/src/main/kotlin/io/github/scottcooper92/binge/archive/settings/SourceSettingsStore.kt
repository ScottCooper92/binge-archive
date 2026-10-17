package io.github.scottcooper92.binge.archive.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [SourceSettings], saved on the device as they are made. A value this build does not know, or one
 * never set, reads as its default. Nothing here sets a value on its own: each setter is a user's choice.
 */
class SourceSettingsStore(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<SourceSettings> = store.data.map { it.toSettings() }

    /** The settings now, for a resolve about to start. */
    suspend fun current(): SourceSettings = settings.first()

    suspend fun setTitleMatches(on: Boolean) {
        store.edit { it[TITLE_MATCHES] = on }
    }

    suspend fun setLicences(level: LicenceLevel) {
        store.edit { it.setOrClear(LICENCES, level, LicenceLevel.DEFAULT) }
    }

    suspend fun setMinimumQuality(quality: MinimumQuality) {
        store.edit { it.setOrClear(MINIMUM_QUALITY, quality, MinimumQuality.ANY) }
    }

    suspend fun setSubtitlesInMyLanguage(on: Boolean) {
        store.edit { it[SUBTITLES_IN_MY_LANGUAGE] = on }
    }

    private fun Preferences.toSettings(): SourceSettings {
        val default = SourceSettings.DEFAULT
        return SourceSettings(
            titleMatches = this[TITLE_MATCHES] ?: default.titleMatches,
            licences = LicenceLevel.entries.firstOrNull { it.name == this[LICENCES] } ?: default.licences,
            minimumQuality = MinimumQuality.entries.firstOrNull { it.name == this[MINIMUM_QUALITY] } ?: default.minimumQuality,
            subtitlesInMyLanguage = this[SUBTITLES_IN_MY_LANGUAGE] ?: default.subtitlesInMyLanguage,
        )
    }

    /** Stores [value] by name, or nothing for the default, so a future default is not pinned. */
    private fun <E : Enum<E>> MutablePreferences.setOrClear(
        key: Preferences.Key<String>,
        value: E,
        default: E,
    ) {
        if (value == default) remove(key) else this[key] = value.name
    }

    private companion object {
        val TITLE_MATCHES = booleanPreferencesKey("title_matches")
        val LICENCES = stringPreferencesKey("licences")
        val MINIMUM_QUALITY = stringPreferencesKey("minimum_quality")
        val SUBTITLES_IN_MY_LANGUAGE = booleanPreferencesKey("subtitles_in_my_language")
    }
}
