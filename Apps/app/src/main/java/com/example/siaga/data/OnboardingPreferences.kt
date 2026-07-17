package com.example.siaga.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Menyimpan apakah user sudah melewati onboarding (SIAGA-02 & 03).
 * Halaman perkenalan dan permintaan izin cuma ditampilkan sekali; sesudah itu
 * splash langsung menuju layar darurat.
 */
class OnboardingPreferences(private val context: Context) {

    private val completedKey = booleanPreferencesKey("onboarding_completed")

    val isCompleted: Flow<Boolean> =
        context.siagaDataStore.data.map { it[completedKey] ?: false }

    suspend fun setCompleted() {
        context.siagaDataStore.edit { it[completedKey] = true }
    }
}
