package com.example.siaga.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Hasil pembacaan GPS yang siap dikirim sebagai payload "user-location". */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val district: String?,
)

/** Pembungkus FusedLocationProvider + reverse-geocode nama district (opsional). */
class LocationClient(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Ambil koordinat GPS terkini. Permission lokasi harus sudah diberikan
     * sebelum fungsi ini dipanggil (dicek di UI).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): UserLocation {
        val location = requestCurrentLocation()
            ?: requestLastKnownLocation()
            ?: throw IllegalStateException("Lokasi tidak tersedia. Pastikan GPS aktif.")

        return UserLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            district = resolveDistrict(location.latitude, location.longitude),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSource = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
            continuation.invokeOnCancellation { cancellationSource.cancel() }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    /** Reverse-geocode nama kecamatan/kelurahan; boleh null kalau gagal. */
    private suspend fun resolveDistrict(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                @Suppress("DEPRECATION")
                val address = Geocoder(context, Locale("id", "ID"))
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                address?.subLocality ?: address?.locality
            } catch (_: Exception) {
                null
            }
        }
}
