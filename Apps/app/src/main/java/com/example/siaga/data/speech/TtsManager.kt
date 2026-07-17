package com.example.siaga.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.example.siaga.data.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Pembacaan instruksi AI. Dua jalur, dipilih lewat [USE_SERVER_TTS]:
 *
 * - TTS bawaan Android (dipakai sekarang): suara robotik, tapi bunyi hampir
 *   seketika karena diproses di HP — untuk aplikasi darurat, cepat > merdu.
 * - Suara neural dari backend (Edge TTS via ExoPlayer): jauh lebih natural,
 *   tapi ada jeda ~1,3 detik (round-trip ke server Microsoft ~0,9 detik yang
 *   tidak bisa ditekan lagi). Cocok kalau butuh kesan natural saat demo.
 *
 * [speak] menerima callback opsional yang dipanggil saat ucapan SELESAI —
 * dipakai ViewModel untuk menunda pembukaan dialer sampai AI selesai bicara.
 */
class TtsManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speakJob: Job? = null
    private var player: ExoPlayer? = null

    // ===== Fallback: engine TTS bawaan Android =====
    private var isLocalReady = false
    private var pendingLocal: Pair<String, (() -> Unit)?>? = null
    private var localOnComplete: (() -> Unit)? = null
    private var localTts: TextToSpeech? = null
    private var localStartedAt = 0L

    init {
        localTts = TextToSpeech(
            context,
            { status ->
                if (status == TextToSpeech.SUCCESS) {
                    configureLocalVoice()
                    isLocalReady = true
                    pendingLocal?.let { (text, onComplete) -> speakLocal(text, onComplete) }
                    pendingLocal = null
                }
            },
            GOOGLE_TTS_ENGINE,
        )
        localTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i(TAG, "TTS lokal mulai bicara (${System.currentTimeMillis() - localStartedAt} ms)")
            }

            override fun onDone(utteranceId: String?) {
                Log.i(TAG, "TTS lokal selesai bicara")
                notifyLocalComplete()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = notifyLocalComplete()
        })
    }

    /**
     * Bacakan [text], memotong ucapan yang sedang berjalan.
     * [onComplete] dipanggil sekali saat ucapan selesai.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        stop()
        if (!USE_SERVER_TTS) {
            speakLocal(text, onComplete)
            return
        }
        speakJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            if (playFromServer(text, startedAt)) {
                onComplete?.invoke()
                return@launch
            }
            ensureActive()
            Log.i(TAG, "Server TTS gagal, pakai TTS lokal")
            speakLocal(text, onComplete)
        }
    }

    /** Hentikan semua ucapan (dipanggil saat user mulai bicara lewat mikrofon). */
    fun stop() {
        speakJob?.cancel()
        speakJob = null
        localOnComplete = null
        pendingLocal = null
        releasePlayer()
        localTts?.stop()
    }

    fun shutdown() {
        stop()
        scope.cancel()
        localTts?.shutdown()
        localTts = null
    }

    // ===== Jalur utama: streaming audio dari server =====

    /**
     * Putar streaming MP3 dari server; suspend sampai ucapan SELESAI.
     * false kalau gagal/terlalu lambat (lanjut ke fallback lokal).
     */
    private suspend fun playFromServer(text: String, startedAt: Long): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                fun finish(result: Boolean) {
                    if (resumed.compareAndSet(false, true)) continuation.resume(result)
                }

                try {
                    val url = "${ServerConfig.BASE_URL}/api/tts?text=" +
                        URLEncoder.encode(text, "UTF-8")

                    // Mulai bunyi begitu ada sedikit audio, jangan nunggu buffer penuh
                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                            BUFFER_FOR_PLAYBACK_MS,
                            BUFFER_AFTER_REBUFFER_MS,
                        )
                        .build()

                    val exoPlayer = ExoPlayer.Builder(context)
                        .setLoadControl(loadControl)
                        .build()
                    player = exoPlayer

                    var started = false
                    exoPlayer.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        false,
                    )
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_READY -> if (!started) {
                                    started = true
                                    Log.i(
                                        TAG,
                                        "Audio server mulai diputar " +
                                            "(${System.currentTimeMillis() - startedAt} ms)"
                                    )
                                }

                                Player.STATE_ENDED -> {
                                    Log.i(TAG, "Audio server selesai diputar")
                                    releasePlayer()
                                    finish(true)
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "ExoPlayer error: ${error.errorCodeName}")
                            releasePlayer()
                            finish(false)
                        }
                    })

                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                    exoPlayer.play()

                    // Watchdog: kalau audio tak kunjung bunyi, pindah ke fallback lokal
                    scope.launch {
                        delay(START_TIMEOUT_MS)
                        if (!started && !resumed.get()) {
                            Log.w(TAG, "Server TTS lambat (> ${START_TIMEOUT_MS}ms)")
                            releasePlayer()
                            finish(false)
                        }
                    }

                    continuation.invokeOnCancellation {
                        scope.launch { releasePlayer() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server TTS gagal: ${e.message}")
                    releasePlayer()
                    finish(false)
                }
            }
        }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    // ===== TTS bawaan Android =====

    private fun speakLocal(text: String, onComplete: (() -> Unit)?) {
        if (!isLocalReady) {
            pendingLocal = text to onComplete
            return
        }
        localOnComplete = onComplete
        localStartedAt = System.currentTimeMillis()
        localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "siaga-instruction")
    }

    private fun notifyLocalComplete() {
        localOnComplete?.invoke()
        localOnComplete = null
    }

    /** Pilih voice id-ID terbaik yang tersedia di perangkat. */
    private fun configureLocalVoice() {
        val engine = localTts ?: return
        engine.language = Locale("id", "ID")
        engine.setSpeechRate(SPEECH_RATE)

        val indonesianVoices = try {
            engine.voices
        } catch (_: Exception) {
            null
        }?.filter { voice ->
            voice.locale.language == "id" &&
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }.orEmpty()

        if (indonesianVoices.isEmpty()) return

        Log.i(TAG, "Voice id-ID tersedia: " + indonesianVoices.joinToString {
            "${it.name}(q=${it.quality}${if (it.isNetworkConnectionRequired) ",network" else ""})"
        })

        // Utamakan voice offline: voice "network" harus ambil audio dari server
        // Google dulu, jadi ada jeda — padahal justru itu yang mau dihindari.
        // Di antara yang offline, ambil kualitas tertinggi.
        val chosen = indonesianVoices.maxByOrNull {
            (if (it.isNetworkConnectionRequired) 0 else OFFLINE_VOICE_BONUS) + it.quality
        }
        chosen?.let {
            engine.voice = it
            Log.i(TAG, "Voice dipakai: ${it.name}")
        }
    }

    private companion object {
        const val TAG = "TtsManager"
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

        /**
         * SAKLAR SUARA:
         * - false = TTS bawaan Android — bunyi hampir seketika, suara robotik.
         * - true  = suara neural dari backend (Edge TTS) — natural, jeda ~1,3 detik.
         *   Butuh backend jalan; kalau gagal otomatis balik ke TTS bawaan.
         */
        const val USE_SERVER_TTS = false

        /** Bikin voice offline selalu menang atas voice network (skala quality 1-5). */
        const val OFFLINE_VOICE_BONUS = 1000

        /** Audio yang harus terkumpul sebelum mulai bunyi — sengaja kecil. */
        const val BUFFER_FOR_PLAYBACK_MS = 150
        const val BUFFER_AFTER_REBUFFER_MS = 500

        /** Batas tunggu suara mulai sebelum pindah ke fallback lokal. */
        const val START_TIMEOUT_MS = 4_000L

        /** Kecepatan bicara fallback lokal; 1.0 normal. */
        const val SPEECH_RATE = 1.05f
    }
}
