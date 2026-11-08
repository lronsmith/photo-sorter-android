package io.github.lronsmith.photosorter.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lronsmith.photosorter.model.SortOrder
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsPreferences: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The app-wide settings DataStore (one instance per process). */
fun Context.settingsDataStore(): DataStore<Preferences> = settingsPreferences

private val EXCLUDED_ALBUM_IDS = stringSetPreferencesKey("excluded_album_ids")
private val ORDER = stringPreferencesKey("order")

class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    // An unreadable file falls back to the defaults instead of crashing the app.
    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val excludedAlbumIds: Flow<Set<String>> =
        prefs.map { it[EXCLUDED_ALBUM_IDS].orEmpty() }.distinctUntilChanged()

    override val order: Flow<SortOrder> = prefs.map { p ->
        SortOrder.entries.firstOrNull { it.name == p[ORDER] } ?: SortOrder.OLDEST_FIRST
    }.distinctUntilChanged()

    override suspend fun setAlbumIncluded(albumId: String, included: Boolean) {
        dataStore.edit { p ->
            val excluded = p[EXCLUDED_ALBUM_IDS].orEmpty()
            p[EXCLUDED_ALBUM_IDS] = if (included) excluded - albumId else excluded + albumId
        }
    }

    override suspend fun setOrder(order: SortOrder) {
        dataStore.edit { it[ORDER] = order.name }
    }
}
