package com.example.siaga.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Pembungkus SpeechRecognizer bawaan Android untuk push-to-talk Bahasa Indonesia.
 * Pola pemakaian: [startListening] saat tombol ditekan, [stopListening] saat
 * dilepas, lalu hasil final datang lewat [onFinal] (atau [onErrorMessage]).
 * Semua callback SpeechRecognizer berjalan di main thread.
 */
class SttManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorMessage("Pengenalan suara tidak tersedia di perangkat ini. Gunakan ketikan.")
            return
        }
        destroy()
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(recognitionListener)
        newRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
    }

    /** Berhenti merekam; hasil final tetap dikirim lewat callback. */
    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                onErrorMessage("Suara tidak terdengar jelas. Tahan tombol lalu bicara.")
            } else {
                onFinal(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onPartial)
        }

        override fun onError(error: Int) {
            onErrorMessage(mapError(error))
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun mapError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Suara tidak terdengar. Tahan tombol dan bicara dengan jelas."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Masalah jaringan saat mengenali suara. Coba lagi."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Izin mikrofon belum diberikan."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Pengenal suara sedang sibuk. Tunggu sebentar lalu coba lagi."
        else -> "Perekaman terlalu singkat. Tahan tombol, bicara, lalu lepaskan."
    }
}
