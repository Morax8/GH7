package com.example.siaga.data.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.sqrt

/**
 * Deteksi benturan keras lewat accelerometer.
 *
 * BATAS YANG PERLU DIKETAHUI: IMU di HP kelas ini (mis. LSM6DSOTR) mentok di
 * ±16 g. Tabrakan mobil sungguhan mencapai 20–100 g, tapi HP yang jatuh ke
 * lantai keras juga tembus 16 g — keduanya sama-sama membuat sensor mentok di
 * angka yang sama. Jadi dari puncak g-force SAJA, benturan tidak bisa dibedakan
 * dari HP jatuh. (iPhone bisa karena punya accelerometer khusus 256 g plus
 * barometer & mikrofon; hardware itu tidak ada di sini.)
 *
 * Pembeda yang benar-benar berguna dan gratis: KECEPATAN sebelum benturan.
 * HP jatuh di tangan = ~0 km/jam; kecelakaan = sedang melaju lalu mendadak
 * berhenti. Lihat [MIN_SPEED_KMH].
 *
 * Catatan: sejak Android 9, aplikasi di background tidak boleh membaca sensor
 * continuous seperti accelerometer. Jadi deteksi ini hanya jalan selama layar
 * aplikasi terbuka. Supaya jalan saat berkendara dengan layar mati, perlu
 * foreground service — belum dibuat.
 */
class CrashDetector(
    private val context: Context,
    private val onImpact: (peakG: Float, speedKmh: Float?) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val fusedLocation =
        LocationServices.getFusedLocationProviderClient(context)

    /** Kecepatan terakhir (km/jam) — diperbarui di latar, dibaca saat benturan. */
    @Volatile
    private var lastSpeedKmh: Float? = null

    private var lastImpactAt = 0L

    fun start() {
        val sensor = accelerometer ?: run {
            Log.w(TAG, "Accelerometer tidak tersedia")
            return
        }
        // Jaga-jaga kalau start() sempat terpanggil dua kali: listener ganda
        // bikin tiap benturan terbaca dobel.
        sensorManager.unregisterListener(this)
        // SENSOR_DELAY_GAME ≈ 50 Hz: cukup menangkap puncak benturan tanpa
        // memaksa sampling rate tinggi yang boros baterai.
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        refreshSpeed()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        // Termasuk gravitasi: saat diam nilainya ~1 g, jadi ambangnya dihitung
        // dari situ dan tidak perlu filter high-pass yang malah menumpulkan puncak.
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (g < IMPACT_THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastImpactAt < DEBOUNCE_MS) return
        lastImpactAt = now

        val speed = lastSpeedKmh
        Log.i(TAG, "Benturan: ${"%.1f".format(g)} g, kecepatan=${speed ?: "?"} km/jam")

        if (MIN_SPEED_KMH > 0f && (speed == null || speed < MIN_SPEED_KMH)) {
            Log.i(TAG, "Diabaikan: kecepatan di bawah ambang ($MIN_SPEED_KMH km/jam)")
            return
        }
        onImpact(g, speed)
        refreshSpeed()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @SuppressLint("MissingPermission")
    private fun refreshSpeed() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        fusedLocation.lastLocation.addOnSuccessListener { location ->
            lastSpeedKmh = location?.takeIf { it.hasSpeed() }?.speed?.times(3.6f)
        }
    }

    companion object {
        private const val TAG = "CrashDetector"

        /**
         * AMBANG KECEPATAN (km/jam) sebelum benturan dianggap kecelakaan.
         *
         * - 0f  = mati. Benturan apa pun memicu — dipakai SEKARANG supaya demo
         *         bisa dengan menggoyang/menjatuhkan HP (kecepatannya 0).
         * - 30f = untuk pemakaian sungguhan. Menyaring HP jatuh, karena HP jatuh
         *         di tangan tidak pernah didahului laju 30 km/jam.
         */
        const val MIN_SPEED_KMH = 0f

        /**
         * Ambang benturan. 3.5 g tidak tercapai oleh jalan kaki atau HP di saku,
         * tapi gampang dicapai goyangan kuat atau jatuh.
         */
        const val IMPACT_THRESHOLD_G = 3.5f

        /** Satu benturan sering menghasilkan beberapa puncak beruntun. */
        const val DEBOUNCE_MS = 3_000L
    }
}
