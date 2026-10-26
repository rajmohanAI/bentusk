package com.bentusk.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bentusk.engine.CaptionLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings: DataStore<Preferences> by preferencesDataStore(name = "bentusk_settings")

data class AppSettings(
    val trialStartMillis: Long,
    val lastSeenMillis: Long,
    /** Last known Pro ownership, so Pro works offline before Play responds. */
    val proCached: Boolean,
    val exportCount: Int,
    val onboardingDone: Boolean,
    /** The user agreed that Bentusk may open Instagram with their images and caption. */
    val instagramConsent: Boolean,
    /** Copy captions with invisible blank lines so Instagram keeps paragraph spacing. */
    val keepLineBreaks: Boolean,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val TrialStart = longPreferencesKey("trial_start")
        val LastSeen = longPreferencesKey("last_seen")
        val ProCached = booleanPreferencesKey("pro_cached")
        val ExportCount = intPreferencesKey("export_count")
        val OnboardingDone = booleanPreferencesKey("onboarding_done")
        val InstagramConsent = booleanPreferencesKey("instagram_consent")
        val Favorites = stringSetPreferencesKey("favorite_templates")
        val KeepLineBreaks = booleanPreferencesKey("keep_line_breaks")
        val CaptionLibrary = stringPreferencesKey("caption_library")
    }

    val settings: Flow<AppSettings> = context.settings.data.map { p ->
        AppSettings(
            trialStartMillis = p[Keys.TrialStart] ?: 0L,
            lastSeenMillis = p[Keys.LastSeen] ?: 0L,
            proCached = p[Keys.ProCached] ?: false,
            exportCount = p[Keys.ExportCount] ?: 0,
            onboardingDone = p[Keys.OnboardingDone] ?: false,
            instagramConsent = p[Keys.InstagramConsent] ?: false,
            keepLineBreaks = p[Keys.KeepLineBreaks] ?: true,
        )
    }

    /** Starts the trial on first launch and records the latest time seen. */
    suspend fun touch(nowMillis: Long) {
        context.settings.edit { p ->
            if ((p[Keys.TrialStart] ?: 0L) == 0L) p[Keys.TrialStart] = nowMillis
            p[Keys.LastSeen] = maxOf(p[Keys.LastSeen] ?: 0L, nowMillis)
        }
    }

    suspend fun setProCached(owned: Boolean) {
        context.settings.edit { it[Keys.ProCached] = owned }
    }

    /** Returns the new total number of exports. */
    suspend fun recordExport(): Int {
        context.settings.edit { it[Keys.ExportCount] = (it[Keys.ExportCount] ?: 0) + 1 }
        return settings.first().exportCount
    }

    suspend fun setOnboardingDone() {
        context.settings.edit { it[Keys.OnboardingDone] = true }
    }

    suspend fun setInstagramConsent(granted: Boolean) {
        context.settings.edit { it[Keys.InstagramConsent] = granted }
    }

    suspend fun setKeepLineBreaks(on: Boolean) {
        context.settings.edit { it[Keys.KeepLineBreaks] = on }
    }

    /** Hashtag sets and captions the user saved for reuse. */
    val captionLibrary: Flow<CaptionLibrary> = context.settings.data.map { CaptionLibrary.fromJson(it[Keys.CaptionLibrary]) }

    suspend fun updateCaptionLibrary(change: (CaptionLibrary) -> CaptionLibrary) {
        context.settings.edit { p ->
            p[Keys.CaptionLibrary] = CaptionLibrary.toJson(change(CaptionLibrary.fromJson(p[Keys.CaptionLibrary])))
        }
    }

    /** Ids of templates the user starred. */
    val favorites: Flow<Set<String>> = context.settings.data.map { it[Keys.Favorites] ?: emptySet() }

    suspend fun toggleFavorite(templateId: String) {
        context.settings.edit { p ->
            val now = p[Keys.Favorites] ?: emptySet()
            p[Keys.Favorites] = if (templateId in now) now - templateId else now + templateId
        }
    }
}
