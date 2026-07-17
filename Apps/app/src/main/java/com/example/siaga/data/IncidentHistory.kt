package com.example.siaga.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.siaga.data.model.EmergencyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Satu laporan darurat yang pernah dikirim user. */
data class Incident(
    val id: String,
    val timestamp: Long,
    val report: String,
    val instruction: String? = null,
    val state: EmergencyState? = null,
    val hospitalName: String? = null,
    val hospitalPhone: String? = null,
)

/**
 * Riwayat laporan darurat, disimpan di HP.
 *
 * Sesi di server terikat ke koneksi socket dan hilang begitu putus, jadi kalau
 * tidak disimpan di sini riwayatnya benar-benar tidak ada. Disimpan sebagai
 * JSON di DataStore: jumlahnya sedikit dan dibatasi [MAX_ENTRIES], jadi belum
 * perlu database.
 */
class IncidentHistory(private val context: Context) {

    private val key = stringPreferencesKey("incidents")

    val incidents: Flow<List<Incident>> =
        context.siagaDataStore.data.map { prefs -> parse(prefs[key]) }

    /** Catat laporan baru; kembalikan id-nya untuk dilengkapi nanti. */
    suspend fun startIncident(report: String): String {
        val incident = Incident(
            id = System.currentTimeMillis().toString(),
            timestamp = System.currentTimeMillis(),
            report = report,
        )
        context.siagaDataStore.edit { prefs ->
            val list = (listOf(incident) + parse(prefs[key])).take(MAX_ENTRIES)
            prefs[key] = serialize(list)
        }
        return incident.id
    }

    /** Lengkapi laporan dengan jawaban AI setelah datang. */
    suspend fun completeIncident(
        id: String,
        instruction: String,
        state: EmergencyState,
        hospitalName: String?,
        hospitalPhone: String?,
    ) {
        context.siagaDataStore.edit { prefs ->
            val list = parse(prefs[key]).map {
                if (it.id != id) it
                else it.copy(
                    instruction = instruction,
                    state = state,
                    hospitalName = hospitalName,
                    hospitalPhone = hospitalPhone,
                )
            }
            prefs[key] = serialize(list)
        }
    }

    suspend fun clear() {
        context.siagaDataStore.edit { it.remove(key) }
    }

    private fun serialize(list: List<Incident>): String {
        val array = JSONArray()
        list.forEach { incident ->
            array.put(
                JSONObject().apply {
                    put("id", incident.id)
                    put("timestamp", incident.timestamp)
                    put("report", incident.report)
                    put("instruction", incident.instruction)
                    put("state", incident.state?.name)
                    put("hospitalName", incident.hospitalName)
                    put("hospitalPhone", incident.hospitalPhone)
                }
            )
        }
        return array.toString()
    }

    private fun parse(raw: String?): List<Incident> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Incident(
                    id = o.getString("id"),
                    timestamp = o.getLong("timestamp"),
                    report = o.getString("report"),
                    instruction = o.optString("instruction").takeIf { it.isNotBlank() },
                    state = o.optString("state").takeIf { it.isNotBlank() }
                        ?.let { runCatching { EmergencyState.valueOf(it) }.getOrNull() },
                    hospitalName = o.optString("hospitalName").takeIf { it.isNotBlank() },
                    hospitalPhone = o.optString("hospitalPhone").takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
