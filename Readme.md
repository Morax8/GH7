# S!GAP — Dispatcher Darurat Bertenaga AI

Saat kecelakaan atau kondisi medis kritis, orang panik: tidak tahu harus berbuat apa, tidak hafal nomor rumah sakit, dan menjelaskan lokasi lewat telepon membuang waktu emas. **S!GAP** memangkas semua itu — cukup **tahan tombol, ceritakan kondisinya dengan suara**, dan aplikasi akan:

1. Memberi instruksi pertolongan pertama lewat suara (AI dispatcher, Bahasa Indonesia)
2. Mengambil lokasi GPS secara otomatis (tidak pernah bertanya "alamatnya di mana?")
3. Mencari rumah sakit terdekat dari database
4. **Menelepon rumah sakit itu secara otomatis** — tanpa satu ketukan pun

---

## 📲 Coba Langsung (Download APK)

> ### [⬇️ Download S!GAP.apk](https://github.com/Morax8/GH7/raw/main/release/SIGAP.apk)
> Android 7.0+ · ±18 MB · backend sudah berjalan di server kami, **tidak perlu setup apa pun**

**Cara install:**
1. Buka link di atas dari HP Android → file APK terunduh.
2. Buka file APK-nya. Jika muncul peringatan *"install dari sumber tidak dikenal"*, izinkan — itu standar untuk semua APK di luar Play Store.
3. Buka aplikasi → ikuti onboarding → izinkan **Lokasi**, **Mikrofon**, dan **Telepon** (ketiganya dipakai fitur inti, bukan opsional).

> ⚠️ **Catatan penting:** panggilan telepon yang dibuat aplikasi itu **sungguhan**. Untuk keperluan demo/penjurian, semua panggilan diarahkan ke nomor demo tim (bukan rumah sakit asli), jadi aman untuk dicoba.

---

## 🎬 Skenario yang Bisa Dicoba

### 1. Laporan darurat dengan suara (fitur utama)
Di layar **Darurat**, **tahan tombol mikrofon besar**, ucapkan misalnya:

> *"Tolong, ada orang tidak sadarkan diri dan sepertinya tidak bernapas"*

lalu lepas. AI akan memberi instruksi pertolongan pertama lewat suara, mengambil GPS otomatis, menampilkan kartu rumah sakit terdekat, lalu **langsung menelepon**. Untuk kondisi yang belum jelas (misal *"teman saya jatuh"*), AI bertanya balik satu pertanyaan dulu sebelum memutuskan.

Tidak bisa bicara? Ketuk ikon **keyboard** untuk mengetik.

### 2. Deteksi benturan (crash detection)
**Guncangkan atau jatuhkan HP** (simulasi benturan kecelakaan). HP akan bergetar + memberi peringatan suara dengan hitung mundur 10 detik — jika tidak dibatalkan, laporan darurat terkirim otomatis. Dirancang untuk korban kecelakaan yang tidak sempat menyentuh HP-nya.

### 3. Widget layar utama
Tambahkan widget **S!GAP** ke home screen → satu ketukan langsung membuka aplikasi dalam mode mendengarkan. Memangkas langkah saat setiap detik berharga.

### 4. Faskes & Riwayat
Tab **Faskes** menampilkan daftar rumah sakit terdekat dari posisimu; tab **Riwayat** mencatat semua insiden yang pernah dilaporkan.

---

## 🏗️ Arsitektur

```
┌─────────────────────┐   Socket.IO    ┌──────────────────────────┐
│  Aplikasi Android    │ ◄────────────► │  Backend Node.js (VPS)    │
│  Kotlin + Compose    │                │  Express + Socket.IO      │
│  STT/TTS on-device   │                │     │            │        │
│  GPS + Auto-call     │                │  Gemini AI     MySQL      │
│  Crash detection     │                │  (dispatcher)  (RS +      │
│  Widget (Glance)     │                │                Haversine) │
└─────────────────────┘                └──────────────────────────┘
```

- **Aplikasi** (`Apps/`): Kotlin, Jetpack Compose, SpeechRecognizer + TextToSpeech on-device (id-ID) supaya respons suara instan tanpa menunggu jaringan, Socket.IO client, accelerometer untuk deteksi benturan, widget Glance.
- **Backend** (`API/`): Express + Socket.IO, Gemini sebagai AI dispatcher (dengan prompt yang melarang pertanyaan bertele-tele saat kondisi kritis), MySQL berisi data rumah sakit dengan pencarian jarak Haversine.

---

## 🛠️ Menjalankan Backend Sendiri (opsional)

APK di atas sudah menunjuk ke server kami, jadi bagian ini hanya jika ingin menjalankan semuanya lokal.

**Prasyarat:** Node.js 18+, MySQL.

```bash
cd API
npm install
```

Buat file `API/.env`:

```env
PORT=3000
GEMINI_API_KEY=your_gemini_api_key        # https://aistudio.google.com/apikey
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=
DB_NAME=siaga_db
DEMO_PHONE=08119711322                     # semua panggilan diarahkan ke sini
```

Isi database (membuat `siaga_db` + data rumah sakit contoh area Tangerang):

```bash
mysql -u root < seed.sql
```

Jalankan:

```bash
node server.js
```

Terakhir, arahkan aplikasi ke server lokal: ubah `BASE_URL` di
`Apps/app/src/main/java/com/example/siaga/data/ServerConfig.kt`, lalu build dari Android Studio
atau `cd Apps && ./gradlew installDebug`.

> Catatan: `DEMO_PHONE` sengaja ada supaya panggilan otomatis tidak pernah menyambung ke rumah sakit sungguhan saat pengujian. Kosongkan hanya jika benar-benar ingin memakai nomor RS asli dari database.

---

## 📁 Struktur Repo

| Folder | Isi |
|---|---|
| `Apps/` | Aplikasi Android (Kotlin + Jetpack Compose) |
| `API/` | Backend Node.js (Express + Socket.IO + Gemini + MySQL) |
| `release/SIGAP.apk` | APK siap install |

---

Dibuat untuk **GH 7.0**.
