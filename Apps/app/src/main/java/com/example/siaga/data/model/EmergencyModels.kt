package com.example.siaga.data.model

/** State analisis AI dari backend: masih bertanya atau kondisi kritis. */
enum class EmergencyState { ASKING, CRITICAL }

/** Data rumah sakit terdekat, dikirim server saat state CRITICAL + lokasi tersedia. */
data class Hospital(
    val name: String,
    val phone: String,
    val address: String,
    val distance: String,
)

/** Payload event "ai-response" dari server. */
data class AiResponse(
    val state: EmergencyState,
    val instruction: String,
    val needLocation: Boolean,
    val hospital: Hospital? = null,
    val action: String? = null,
    /**
     * Teks siap-baca dari server (instruksi + info RS). Server sudah mulai
     * menyintesis teks ini saat mengirim response, jadi memakainya apa adanya
     * membuat audio tersedia hampir seketika. Null → susun sendiri di client.
     */
    val speech: String? = null,
) {
    companion object {
        const val ACTION_DIAL_EMERGENCY = "DIAL_EMERGENCY"
    }
}
