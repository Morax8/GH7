package com.example.siaga.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.siaga.data.ServerConfig
import com.example.siaga.data.location.LocationClient
import com.example.siaga.data.location.UserLocation
import com.example.siaga.data.model.AiResponse
import com.example.siaga.data.socket.EmergencySocketClient
import com.example.siaga.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val socketClient = EmergencySocketClient()
    private val locationClient = LocationClient(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ChatEffect> = _effects

    /** Pesan terakhir user, untuk auto-resend setelah lokasi diterima server. */
    private var lastUserMessage: String? = null

    /** Lokasi terakhir yang berhasil didapat, dikirim ulang saat reconnect. */
    private var lastLocation: UserLocation? = null

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
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        lastUserMessage = trimmed
        autoLocationAttempts = 0
        addMessage(ChatMessage(sender = Sender.USER, text = trimmed))
        socketClient.sendUserMessage(trimmed)
        _uiState.update { it.copy(isAnalyzing = true) }
    }

    fun retryConnection() {
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
        socketClient.retryConnect()
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
                autoLocationAttempts++
                val storedLocation = lastLocation
                when {
                    // Pengaman: server terus meminta lokasi -> berhenti otomatis
                    autoLocationAttempts > MAX_AUTO_LOCATION_ATTEMPTS -> Unit
                    // Lokasi sudah pernah didapat: kirim ulang tanpa fetch GPS lagi
                    storedLocation != null -> {
                        resendMessageAfterLocationAck = true
                        socketClient.sendUserLocation(
                            latitude = storedLocation.latitude,
                            longitude = storedLocation.longitude,
                            district = storedLocation.district,
                        )
                    }
                    else -> _effects.tryEmit(ChatEffect.RequestLocation)
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
        addMessage(
            ChatMessage(
                sender = Sender.AI,
                text = response.instruction,
                state = response.state,
                hospital = response.hospital,
            )
        )
        val phone = response.hospital?.phone
        if (response.action == AiResponse.ACTION_DIAL_EMERGENCY && !phone.isNullOrBlank()) {
            _effects.tryEmit(ChatEffect.OpenDialer(phone))
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    override fun onCleared() {
        socketClient.disconnect()
        super.onCleared()
    }

    private companion object {
        const val MAX_AUTO_LOCATION_ATTEMPTS = 3
    }
}
