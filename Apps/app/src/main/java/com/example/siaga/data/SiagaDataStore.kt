package com.example.siaga.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Satu DataStore untuk seluruh aplikasi. Delegate-nya hanya boleh dideklarasikan
 * sekali per nama file, jadi ditaruh di sini dan dipakai bersama
 * ([OnboardingPreferences], [IncidentHistory]).
 */
internal val Context.siagaDataStore by preferencesDataStore(name = "siaga_prefs")
