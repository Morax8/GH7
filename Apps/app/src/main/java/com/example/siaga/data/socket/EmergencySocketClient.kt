package com.example.siaga.data.socket

import com.example.siaga.data.ServerConfig
import com.example.siaga.data.model.AiResponse
import com.example.siaga.data.model.EmergencyState
import com.example.siaga.data.model.Hospital
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/** Semua kejadian dari socket yang perlu direspon oleh ViewModel. */
sealed interface SocketEvent {
    data object Connected : SocketEvent
    data object Disconnected : SocketEvent
    data object Reconnecting : SocketEvent
    data class ConnectError(val detail: String?) : SocketEvent
    data class AiResponseReceived(val response: AiResponse) : SocketEvent
    data class AskLocation(val instruction: String, val state: EmergencyState) : SocketEvent
    data class LocationAck(val message: String) : SocketEvent
    data class ServerError(val message: String) : SocketEvent
}

/**
 * Pembungkus koneksi Socket.IO ke backend Emergency Dispatcher.
 * Terpisah dari UI; ViewModel cukup collect [events] dan panggil fungsi emit.
 *
 * Kontrak event:
 * - kirim  : "user-message" (string mentah), "user-location" ({latitude, longitude, district})
 * - terima : "ai-response", "ask-location", "location-response", "error"
 */
class EmergencySocketClient(private val serverUrl: String = ServerConfig.BASE_URL) {

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events

    private var socket: Socket? = null

    fun connect() {
        if (socket != null) return

        val options = IO.Options().apply {
            // Sesi darurat di server terikat ke satu koneksi socket:
            // reconnection otomatis wajib aktif supaya sesi cepat pulih.
            reconnection = true
            reconnectionDelay = 1_000
            reconnectionDelayMax = 5_000
            timeout = 10_000
        }

        val newSocket = IO.socket(serverUrl, options)
        socket = newSocket

        newSocket.on(Socket.EVENT_CONNECT) {
            _events.tryEmit(SocketEvent.Connected)
        }
        newSocket.on(Socket.EVENT_DISCONNECT) {
            _events.tryEmit(SocketEvent.Disconnected)
        }
        newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            _events.tryEmit(SocketEvent.ConnectError(args.firstOrNull()?.toString()))
        }
        newSocket.io().on(Manager.EVENT_RECONNECT_ATTEMPT) {
            _events.tryEmit(SocketEvent.Reconnecting)
        }

        newSocket.on("ai-response") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(SocketEvent.AiResponseReceived(parseAiResponse(json)))
        }
        newSocket.on("ask-location") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(
                SocketEvent.AskLocation(
                    instruction = json.optString("instruction"),
                    state = parseState(json.optString("state")),
                )
            )
        }
        newSocket.on("location-response") { args ->
            val json = args.firstOrNull() as? JSONObject
            _events.tryEmit(SocketEvent.LocationAck(json?.optString("message").orEmpty()))
        }
        newSocket.on("error") { args ->
            val json = args.firstOrNull() as? JSONObject
            val message = json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Terjadi kesalahan pada server."
            _events.tryEmit(SocketEvent.ServerError(message))
        }

        newSocket.connect()
    }

    /** Paksa coba konek lagi sekarang (tombol retry di UI). */
    fun retryConnect() {
        val current = socket ?: return connect()
        if (!current.connected()) current.connect()
    }

    /** Payload "user-message" harus string mentah, bukan object. */
    fun sendUserMessage(text: String) {
        socket?.emit("user-message", text)
    }

    fun sendUserLocation(latitude: Double, longitude: Double, district: String?) {
        val payload = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("district", district ?: JSONObject.NULL)
        }
        socket?.emit("user-location", payload)
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    private fun parseAiResponse(json: JSONObject): AiResponse {
        val hospitalJson = json.optJSONObject("hospital")
        return AiResponse(
            state = parseState(json.optString("state")),
            instruction = json.optString("instruction"),
            needLocation = json.optBoolean("need_location", false),
            hospital = hospitalJson?.let {
                Hospital(
                    name = it.optString("name"),
                    phone = it.optString("phone"),
                    address = it.optString("address"),
                    distance = it.optString("distance"),
                )
            },
            action = json.optString("action").takeIf { it.isNotBlank() },
        )
    }

    private fun parseState(raw: String?): EmergencyState =
        if (raw.equals("CRITICAL", ignoreCase = true)) EmergencyState.CRITICAL
        else EmergencyState.ASKING
}
