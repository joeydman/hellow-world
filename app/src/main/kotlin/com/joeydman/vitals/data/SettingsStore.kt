package com.joeydman.vitals.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val designCapacityKey = intPreferencesKey("design_capacity_override_mah")

    /** User override for design capacity; null means use the built-in device profile. */
    val designCapacityOverride: Flow<Int?> = context.dataStore.data.map { it[designCapacityKey] }

    suspend fun designCapacityOverrideNow(): Int? = designCapacityOverride.first()

    suspend fun setDesignCapacityOverride(mah: Int?) {
        context.dataStore.edit { prefs ->
            if (mah == null) prefs.remove(designCapacityKey) else prefs[designCapacityKey] = mah
        }
    }
}
