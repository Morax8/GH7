package com.example.siaga.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.siaga.data.IncidentHistory
import com.example.siaga.data.ServerConfig
import com.example.siaga.data.location.LocationClient
import com.example.siaga.data.location.UserLocation
import com.example.siaga.data.sensor.CrashAlarm
import com.example.siaga.data.sensor.CrashDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.siaga.data.model.AiResponse
import com.example.siaga.data.socket.EmergencySocketClient
import com.example.siaga.data.socket.SocketEvent
import com.example.siaga.data.speech.SttManager
import com.example.siaga.data.speech.TtsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val socketClient = EmergencySocketClient()
    private val locationClient = LocationClient(application)
    private val history = IncidentHistory(application)
    private val crashDetector = CrashDetector(application, ::onImpactDetected)
    private val crashAlarm = CrashAlarm(application)
    private var crashCountdownJob: Job? = null

    /** Laporan yang sedang berjalan di riwayat, dilengkapi saat AI menjawab. */
    private var currentIncidentId: String? = null
    private val ttsManager = TtsManager(application)
    private val sttManager = SttManager(
        context = application,
        onPartial = { text -> _uiState.update { it.copy(partialTranscript = text) } },
        onFinal = ::onSpeechResult,
        onErrorMessage = ::onSpeechError,
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ChatEffect> = _effects

    /** Pesan terakhir user, untuk auto-resend setelah lokasi diterima server. */
    private var lastUserMessage: String? = null

    /** Lokasi terakhir yang berhasil didapat, dikirim ulang saat reconnect. */
    private var lastLocation: UserLocation? = null

    /**
     * Instruksi dari "ask-location" yang belum dibacakan karena menunggu
     * instruksi pengganti dari server. Dibacakan kalau alur lokasi gagal,
     * supaya panduannya tetap sampai ke user.
     */
    private var pendingLocationInstruction: String? = null

    /**
     * true hanya saat lokasi dikirim sebagai bagian alur "ask-location".
     * Server tidak melanjutkan analisis otomatis setelah "location-response",
     * jadi client wajib mengirim ulang pesan terakhir saat flag ini aktif.
     */
    private var resendMessageAfterLocationAck = false

    /**
     * Pengaman loop: jumlah "ask-location" yang direspon otomatis untuk satu
     * pesan user. Direset setiap user mengirim pesan baru.
     */
    private var autoLocationAttempts = 0

    /** Pembeda koneksi pertama vs reconnect (sesi server hilang saat reconnect). */
    private var wasConnected = false

    /** Supaya pesan "server tidak terjangkau" tidak muncul di setiap percobaan retry. */
    private var connectErrorShown = false

    init {
        viewModelScope.launch {
            socketClient.events.collect(::onSocketEvent)
        }
        socketClient.connect()
        crashDetector.start()
    }

    /**
     * Benturan keras terdeteksi. TIDAK langsung melapor: dihitung mundur dulu
     * supaya user bisa membatalkan. Aplikasi ini menelepon otomatis saat kritis
     * — kalau sensor salah baca dan langsung jalan, yang tertelepon orang
     * sungguhan tanpa ada keadaan darurat.
     */
    private fun onImpactDetected(peakG: Float, speedKmh: Float?) {
        // Jangan ganggu kalau user sudah/sedang melapor sendiri
        if (crashCountdownJob?.isActive == true) return
        if (_uiState.value.isListening || _uiState.value.isAnalyzing) return

        addMessage(
            ChatMessage(
                sender = Sender.SYSTEM,
                text = "Benturan keras terdeteksi (${"%.1f".format(peakG)} g)" +
                    (speedKmh?.let { " pada ${it.toInt()} km/jam" } ?: "") + ".",
            )
        )

        crashAlarm.start()
        // Getar saja bisa terlewat kalau HP di tas atau dashboard. Kalimatnya
        // sengaja panjang supaya terdengar hampir sepanjang countdown, dan
        // menyebut cara membatalkan — user mungkin sedang bingung.
        ttsManager.speak(
            "Benturan terdeteksi. Laporan darurat akan dikirim dalam sepuluh detik. " +
                "Tekan batal kalau Anda tidak apa-apa."
        )

        crashCountdownJob = viewModelScope.launch {
            try {
                for (remaining in CRASH_COUNTDOWN_SECONDS downTo 1) {
                    _uiState.update { it.copy(crashCountdown = remaining) }
                    delay(1_000)
                }
                _uiState.update { it.copy(crashCountdown = null) }
                sendMessage(CRASH_REPORT)
            } finally {
                // finally, bukan setelah loop: kalau job dibatalkan (user menekan
                // batal, atau ViewModel mati), getarnya harus tetap berhenti.
                crashAlarm.stop()
            }
        }
    }

    /** User menekan "Saya tidak apa-apa" — batalkan laporan otomatis. */
    fun cancelCrashCountdown() {
        crashCountdownJob?.cancel()
        crashCountdownJob = null
        crashAlarm.stop()
        ttsManager.stop()
        _uiState.update { it.copy(crashCountdown = null) }
        addMessage(
            ChatMessage(sender = Sender.SYSTEM, text = "Laporan otomatis dibatalkan.")
        )
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        lastUserMessage = trimmed
        autoLocationAttempts = 0
        addMessage(ChatMessage(sender = Sender.USER, text = trimmed))
        socketClient.sendUserMessage(trimmed)
        _uiState.update { it.copy(isAnalyzing = true) }

        // Dicatat sekarang, dilengkapi saat AI menjawab. Hanya dipanggil dari
        // laporan user — kiriman ulang otomatis lewat socketClient langsung,
        // jadi tidak bikin entri dobel.
        viewModelScope.launch { currentIncidentId = history.startIncident(trimmed) }
    }

    fun retryConnection() {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
        socketClient.retryConnect()
    }

    /** Tombol mikrofon ditekan (permission mikrofon sudah dicek di UI). */
    fun startVoiceInput() {
        ttsManager.stop() // jangan bicara bareng user
        _uiState.update { it.copy(isListening = true, partialTranscript = null) }
        sttManager.startListening()
    }

    /** Tombol mikrofon dilepas; hasil final menyusul lewat callback STT. */
    fun stopVoiceInput() {
        if (!_uiState.value.isListening) return
        sttManager.stopListening()
    }

    fun onMicPermissionDenied() {
        _uiState.update { it.copy(isListening = false, partialTranscript = null) }
        addMessage(
            ChatMessage(
                sender = Sender.SYSTEM,
                text = "Izin mikrofon ditolak. Aktifkan izin mikrofon di Pengaturan, " +
                    "atau gunakan ketikan lewat ikon keyboard.",
                isError = true,
            )
        )
    }

    private fun onSpeechResult(text: String) {
        _uiState.update { it.copy(isListening = false, partialTranscript = null) }
        sendMessage(text)
    }

    private fun onSpeechError(message: String) {
        _uiState.update { it.copy(isListening = false, partialTranscript = null) }
        addMessage(ChatMessage(sender = Sender.SYSTEM, text = message, isError = true))
    }

    /** Dipanggil UI setelah permission lokasi dipastikan granted. */
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            addMessage(ChatMessage(sender = Sender.SYSTEM, text = "Mengambil lokasi GPS Anda…"))
            try {
                val location = locationClient.getCurrentLocation()
                lastLocation = location
                resendMessageAfterLocationAck = true
                socketClient.sendUserLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    district = location.district,
                )
            } catch (e: Exception) {
                addMessage(
                    ChatMessage(
                        sender = Sender.SYSTEM,
                        text = "Gagal mengambil lokasi (${e.message ?: "tidak diketahui"}). " +
                            "Pastikan GPS aktif, lalu kirim pesan Anda sekali lagi.",
                        isError = true,
                    )
                )
                // Tidak ada instruksi pengganti dari server → bacakan yang tertunda
                speakPendingLocationInstruction()
            }
        }
    }

    fun onLocationPermissionDenied() {
        addMessage(
            ChatMessage(
                sender = Sender.SYSTEM,
                text = "Izin lokasi ditolak. Tanpa lokasi kami tidak bisa mencari RS terdekat. " +
                    "Aktifkan izin lokasi di Pengaturan, lalu kirim pesan Anda sekali lagi.",
                isError = true,
            )
        )
        // Tidak ada instruksi pengganti dari server → bacakan yang tertunda
        speakPendingLocationInstruction()
    }

    private fun speakPendingLocationInstruction() {
        pendingLocationInstruction?.let { ttsManager.speak(it) }
        pendingLocationInstruction = null
    }

    private fun onSocketEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.Connected -> {
                connectErrorShown = false
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
                if (wasConnected) {
                    // Sesi di server hilang setelah reconnect: kirim ulang lokasi
                    // (kalau sudah pernah didapat) sebelum pesan berikutnya.
                    lastLocation?.let {
                        socketClient.sendUserLocation(it.latitude, it.longitude, it.district)
                    }
                    addMessage(ChatMessage(sender = Sender.SYSTEM, text = "Terhubung kembali ke server."))
                }
                wasConnected = true
            }

            is SocketEvent.Disconnected -> {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.DISCONNECTED) }
            }

            is SocketEvent.Reconnecting -> {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
            }

            is SocketEvent.ConnectError -> {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, isAnalyzing = false)
                }
                if (!connectErrorShown) {
                    connectErrorShown = true
                    addMessage(
                        ChatMessage(
                            sender = Sender.SYSTEM,
                            text = "Server tidak terjangkau (${ServerConfig.BASE_URL}). " +
                                "Periksa koneksi dan pastikan backend berjalan. Mencoba menghubungkan ulang…",
                            isError = true,
                        )
                    )
                }
            }

            is SocketEvent.AiResponseReceived -> onAiResponse(event.response)

            is SocketEvent.AskLocation -> {
                _uiState.update { it.copy(isAnalyzing = false) }
                addMessage(
                    ChatMessage(sender = Sender.AI, text = event.instruction, state = event.state)
                )

                // Sengaja belum dibacakan: begitu lokasi terkirim, server langsung
                // mengirim 'ai-response' berisi instruksi pengganti — kalau dibacakan
                // sekarang, suaranya pasti terpotong di tengah kalimat. Teksnya tetap
                // tampil di transkrip, dan dibacakan kalau alur lokasi gagal.
                autoLocationAttempts++
                val storedLocation = lastLocation
                when {
                    // Pengaman: server terus meminta lokasi -> berhenti otomatis.
                    // Tidak ada respon lanjutan, jadi instruksi ini yang final.
                    autoLocationAttempts > MAX_AUTO_LOCATION_ATTEMPTS ->
                        ttsManager.speak(event.instruction)

                    // Lokasi sudah pernah didapat: kirim ulang tanpa fetch GPS lagi
                    storedLocation != null -> {
                        pendingLocationInstruction = event.instruction
                        resendMessageAfterLocationAck = true
                        socketClient.sendUserLocation(
                            latitude = storedLocation.latitude,
                            longitude = storedLocation.longitude,
                            district = storedLocation.district,
                        )
                    }

                    else -> {
                        pendingLocationInstruction = event.instruction
                        _effects.tryEmit(ChatEffect.RequestLocation)
                    }
                }
            }

            is SocketEvent.LocationAck -> {
                if (resendMessageAfterLocationAck) {
                    resendMessageAfterLocationAck = false
                    if (event.message.isNotBlank()) {
                        addMessage(ChatMessage(sender = Sender.SYSTEM, text = event.message))
                    }
                    // ATURAN ALUR: server berhenti setelah menyimpan lokasi.
                    // Kirim ulang pesan terakhir supaya analisis dilanjutkan.
                    lastUserMessage?.let {
                        socketClient.sendUserMessage(it)
                        _uiState.update { state -> state.copy(isAnalyzing = true) }
                    }
                }
            }

            is SocketEvent.ServerError -> {
                _uiState.update { it.copy(isAnalyzing = false) }
                addMessage(ChatMessage(sender = Sender.SYSTEM, text = event.message, isError = true))
            }
        }
    }

    private fun onAiResponse(response: AiResponse) {
        _uiState.update { it.copy(isAnalyzing = false) }
        // Instruksi ini menggantikan instruksi 'ask-location' yang tertunda
        pendingLocationInstruction = null
        addMessage(
            ChatMessage(
                sender = Sender.AI,
                text = response.instruction,
                state = response.state,
                hospital = response.hospital,
            )
        )
        currentIncidentId?.let { id ->
            viewModelScope.launch {
                history.completeIncident(
                    id = id,
                    instruction = response.instruction,
                    state = response.state,
                    hospitalName = response.hospital?.name,
                    hospitalPhone = response.hospital?.phone,
                )
            }
        }

        val hospital = response.hospital
        // Pakai teks dari server kalau ada — audionya sudah mulai disiapkan di sana.
        val spokenText = response.speech ?: if (hospital != null) {
            response.instruction +
                " Rumah sakit terdekat: ${hospital.name}, jarak ${hospital.distance}. " +
                "Membuka panggilan darurat."
        } else {
            response.instruction
        }

        val phone = hospital?.phone
        if (response.action == AiResponse.ACTION_DIAL_EMERGENCY && !phone.isNullOrBlank()) {
            // Biarkan AI selesai bicara dulu baru menelepon.
            // Pengaman: apa pun yang terjadi pada TTS, panggilan tetap jalan
            // paling lambat DIAL_FALLBACK_MS setelah respon diterima.
            val dialFallback = viewModelScope.launch {
                delay(DIAL_FALLBACK_MS)
                _effects.tryEmit(ChatEffect.PlaceCall(phone))
            }
            ttsManager.speak(spokenText) {
                if (dialFallback.isActive) {
                    dialFallback.cancel()
                    _effects.tryEmit(ChatEffect.PlaceCall(phone))
                }
            }
        } else {
            ttsManager.speak(spokenText)
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    override fun onCleared() {
        crashAlarm.stop()
        crashDetector.stop()
        sttManager.destroy()
        ttsManager.shutdown()
        socketClient.disconnect()
        super.onCleared()
    }

    private companion object {
        const val MAX_AUTO_LOCATION_ATTEMPTS = 3

        /** Waktu bagi user untuk membatalkan sebelum laporan otomatis dikirim. */
        const val CRASH_COUNTDOWN_SECONDS = 10

        /** Laporan yang dikirim ke AI kalau benturan tidak dibatalkan. */
        const val CRASH_REPORT = "Terdeteksi benturan keras, kemungkinan kecelakaan " +
            "kendaraan. Saya tidak merespons dan mungkin tidak sadarkan diri."

        /**
         * Pengaman kalau TTS macet total: dialer tetap dibuka. Harus lebih lama
         * dari durasi bicara wajar (instruksi + info RS terukur ~18 detik),
         * kalau tidak dialer malah menyela AI yang masih bicara. Kartu RS dengan
         * tombol telepon sudah tampil di layar, jadi user tak pernah terkunci.
         */
        const val DIAL_FALLBACK_MS = 30_000L
    }
}
