package com.example.siaga.ui.chat

import com.example.siaga.data.model.EmergencyState
import com.example.siaga.data.model.Hospital
import java.util.UUID

/** Status koneksi socket untuk indikator di top bar. */
enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED }

enum class Sender { USER, AI, SYSTEM }

/** Satu bubble di layar chat darurat. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val state: EmergencyState? = null,
    val hospital: Hospital? = null,
    val isError: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isAnalyzing: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
)

/** Efek satu-kali yang harus dieksekusi oleh layer UI (butuh Context/Activity). */
sealed interface ChatEffect {
    /** Minta permission lokasi lalu ambil GPS (respon event "ask-location"). */
    data object RequestLocation : ChatEffect

    /** Buka dialer telepon terisi nomor RS (respon action DIAL_EMERGENCY). */
    data class OpenDialer(val phoneNumber: String) : ChatEffect
}
