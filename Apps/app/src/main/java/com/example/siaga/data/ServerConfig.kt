package com.example.siaga.data

/**
 * Konfigurasi alamat backend Emergency Dispatcher.
 *
 * - HP fisik via USB  : "http://127.0.0.1:3000" + jalankan sekali di laptop:
 *                       adb reverse tcp:3000 tcp:3000
 *                       (ulangi setiap kabel dicabut-pasang)
 * - HP fisik via Wi-Fi: IP laptop di jaringan yang sama, contoh
 *                       "http://192.168.1.10:3000" (pastikan Wi-Fi tidak
 *                       memblokir koneksi antar-perangkat)
 * - Emulator Android  : "http://10.0.2.2:3000" (10.0.2.2 = localhost laptop)
 */
object ServerConfig {
    const val BASE_URL = "http://127.0.0.1:3000"
}
