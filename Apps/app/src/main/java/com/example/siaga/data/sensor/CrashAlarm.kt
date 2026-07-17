package com.example.siaga.data.sensor

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Getaran peringatan saat benturan terdeteksi.
 *
 * Sengaja berulang selama countdown, bukan sekali di awal: kalau HP terlempar
 * atau ada di saku, countdown di layar tidak ada yang lihat. Getaran ini yang
 * memberi tahu user bahwa laporan darurat akan dikirim dan masih bisa
 * dibatalkan.
 *
 * PENTING — getaran ini dikirim sebagai USAGE_ALARM, bukan getaran biasa.
 * Getaran tanpa atribut dianggap sistem sebagai USAGE_UNKNOWN dan tunduk pada
 * setelan intensitas "haptic feedback". Kalau user mematikan getar-saat-disentuh
 * (haptic_feedback_enabled = 0 — umum sekali), getarannya dibungkam total tanpa
 * error. Untuk peringatan darurat itu tidak boleh terjadi: ini alarm, bukan
 * umpan balik sentuhan.
 */
class CrashAlarm(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start() {
        val v = vibrator
        Log.i(
            TAG,
            "start(): vibrator=${if (v == null) "NULL" else "ada"}, " +
                "hasVibrator=${v?.hasVibrator()}, " +
                "amplitudeControl=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v?.hasAmplitudeControl() else null}, " +
                "sdk=${Build.VERSION.SDK_INT}"
        )
        if (v == null || !v.hasVibrator()) return

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                v.vibrate(waveform(), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // Sebelum API 30 atributnya lewat AudioAttributes
                v.vibrate(waveform(), alarmAudioAttributes())
            }

            else -> {
                @Suppress("DEPRECATION")
                v.vibrate(PATTERN, 0, alarmAudioAttributes())
            }
        }
    }

    fun stop() {
        vibrator?.cancel()
    }

    /** Amplitudo dipatok penuh (255) supaya tidak ikut diperlemah setelan. */
    private fun waveform(): VibrationEffect =
        VibrationEffect.createWaveform(PATTERN, AMPLITUDES, 0)

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private companion object {
        const val TAG = "CrashAlarm"

        /** Tunggu 0, getar 500ms, diam 500ms — denyut mendesak, diulang terus. */
        val PATTERN = longArrayOf(0, 500, 500)

        /** Sejajar dengan PATTERN: diam, penuh, diam. Indeks 0 = ulangi dari awal. */
        val AMPLITUDES = intArrayOf(0, 255, 0)
    }
}
