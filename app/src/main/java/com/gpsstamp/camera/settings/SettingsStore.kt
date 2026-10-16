package com.gpsstamp.camera.settings

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists the stamp note and the last-used camera between launches. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val noteKey = stringPreferencesKey("note")
    private val lensKey = intPreferencesKey("lens_facing")

    val note: Flow<String> = store.data.map { it[noteKey] ?: "" }
    val lensFacing: Flow<Int> = store.data.map { it[lensKey] ?: CameraSelector.LENS_FACING_BACK }

    suspend fun setNote(value: String) {
        store.edit { it[noteKey] = value }
    }

    suspend fun setLensFacing(value: Int) {
        store.edit { it[lensKey] = value }
    }
}
