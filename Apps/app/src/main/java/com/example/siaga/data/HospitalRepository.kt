package com.example.siaga.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Satu faskes beserta jaraknya dari user (null kalau lokasi belum diketahui). */
data class NearbyHospital(
    val name: String,
    val phone: String,
    val address: String,
    val district: String?,
    val distanceKm: Double?,
)

/**
 * Daftar faskes untuk halaman Faskes.
 *
 * Backend sudah punya `GET /api/hospitals` yang mengembalikan semua RS lengkap
 * dengan koordinat, jadi jaraknya dihitung di sini saja — tidak perlu endpoint
 * baru. Server hanya punya endpoint "RS terdekat" yang mengembalikan satu.
 */
class HospitalRepository {

    suspend fun fetchNearby(
        latitude: Double?,
        longitude: Double?,
    ): List<NearbyHospital> = withContext(Dispatchers.IO) {
        val connection = URL("${ServerConfig.BASE_URL}/api/hospitals")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000

        val body = try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val data = JSONObject(body).getJSONArray("data")
        val hospitals = (0 until data.length()).map { i ->
            val o = data.getJSONObject(i)
            NearbyHospital(
                name = o.getString("name"),
                phone = o.getString("phone"),
                address = o.getString("address"),
                district = o.optString("district").takeIf { it.isNotBlank() },
                distanceKm = if (latitude != null && longitude != null) {
                    haversineKm(latitude, longitude, o.getDouble("latitude"), o.getDouble("longitude"))
                } else {
                    null
                },
            )
        }
        // Yang terdekat di atas; kalau lokasi belum ada, biarkan urutan server
        hospitals.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

    /** Jarak lingkaran besar antara dua koordinat, dalam kilometer. */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a))
    }
}
