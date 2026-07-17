package com.example.siaga.data

/**
 * Konfigurasi alamat backend Emergency Dispatcher.
 *
 * - VPS (dipakai sekarang): "http://116.193.191.110:3000" — jalan di mana saja
 *                       asal HP punya internet, tidak perlu kabel/satu Wi-Fi.
 *
 * Alternatif kalau backend dijalankan di laptop sendiri:
 * - HP fisik via USB  : "http://127.0.0.1:3000" + jalankan sekali di laptop:
 *                       adb reverse tcp:3000 tcp:3000
 * - HP fisik via Wi-Fi: IP laptop di jaringan yang sama, contoh
 *                       "http://192.168.1.10:3000"
 * - Emulator Android  : "http://10.0.2.2:3000" (10.0.2.2 = localhost laptop)
 */
object ServerConfig {
    const val BASE_URL = "http://116.193.191.110:3000"
}
