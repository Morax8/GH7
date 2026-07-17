# S!GAP — AI-Powered Emergency Dispatcher

In an accident or a medical emergency, people panic: they don't know what to do, don't know any hospital's number, and describing their location over the phone wastes golden minutes. **S!GAP** cuts all of that out — just **hold the button and describe the situation with your voice**, and the app will:

1. Give first-aid instructions out loud (AI dispatcher, in Indonesian)
2. Grab your GPS location automatically (it never asks "where are you?")
3. Find the nearest hospital from its database
4. **Call that hospital automatically** — without a single extra tap

---

## 📲 Try It Now (Download APK)

> ### [⬇️ Download S!GAP.apk](https://github.com/Morax8/GH7/raw/main/release/SIGAP.apk)
> Android 7.0+ · ±18 MB · the backend is already running on our server — **no setup required**

**How to install:**
1. Open the link above on an Android phone → the APK downloads.
2. Open the APK file. If you see an *"install from unknown sources"* warning, allow it — that's standard for any APK outside the Play Store.
3. Open the app → follow the onboarding → grant **Location**, **Microphone**, and **Phone** permissions (all three power core features, they are not optional).

> ⚠️ **Important:** the phone calls the app places are **real**. For demo/judging purposes, every call is routed to our team's demo number (not an actual hospital), so it's safe to try.

---

## 🎬 Things to Try

### 1. Voice emergency report (the core feature)
On the **Darurat** (Emergency) screen, **hold the big microphone button** and say, for example:

> *"Tolong, ada orang tidak sadarkan diri dan sepertinya tidak bernapas"*
> ("Help, someone is unconscious and doesn't seem to be breathing")

then release. The AI speaks first-aid instructions, grabs GPS automatically, shows a card with the nearest hospital, then **places the call immediately**. For unclear situations (e.g. *"my friend fell"*), the AI asks one clarifying question first before deciding.

Can't speak? Tap the **keyboard** icon to type instead.

> Note: speech recognition and the AI dispatcher currently work in **Indonesian**.

### 2. Crash detection
**Shake or drop the phone** (simulating an accident impact). The phone vibrates + speaks a warning with a 10-second countdown — if not cancelled, an emergency report is sent automatically. Designed for accident victims who never get the chance to touch their phone.

### 3. Home-screen widget
Add the **S!GAP** widget to your home screen → one tap opens the app already listening. Fewer steps when every second counts.

### 4. Faskes & Riwayat
The **Faskes** (Facilities) tab lists the hospitals nearest to you; the **Riwayat** (History) tab keeps a record of every incident you've reported.

---

## 🏗️ Architecture

```
┌─────────────────────┐   Socket.IO    ┌──────────────────────────┐
│  Android App         │ ◄────────────► │  Node.js Backend (VPS)    │
│  Kotlin + Compose    │                │  Express + Socket.IO      │
│  On-device STT/TTS   │                │     │            │        │
│  GPS + Auto-call     │                │  Gemini AI     MySQL      │
│  Crash detection     │                │  (dispatcher)  (hospitals │
│  Widget (Glance)     │                │                Haversine) │
└─────────────────────┘                └──────────────────────────┘
```

- **App** (`Apps/`): Kotlin, Jetpack Compose, on-device SpeechRecognizer + TextToSpeech (id-ID) so voice responses are instant with no network round-trip, Socket.IO client, accelerometer-based crash detection, Glance widget.
- **Backend** (`API/`): Express + Socket.IO, Gemini as the AI dispatcher (with a prompt that forbids long-winded questions in critical situations), MySQL hospital database with Haversine distance search.

---

## 🛠️ Running the Backend Yourself (optional)

The APK above already points to our server, so this section is only needed if you want to run everything locally.

**Prerequisites:** Node.js 18+, MySQL.

```bash
cd API
npm install
```

Create `API/.env`:

```env
PORT=3000
GEMINI_API_KEY=your_gemini_api_key        # https://aistudio.google.com/apikey
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=
DB_NAME=siaga_db
DEMO_PHONE=08119711322                     # all calls are routed here
```

Seed the database (creates `siaga_db` + sample hospitals for the Tangerang area):

```bash
mysql -u root < seed.sql
```

Run it:

```bash
node server.js
```

Finally, point the app at your local server: change `BASE_URL` in
`Apps/app/src/main/java/com/example/siaga/data/ServerConfig.kt`, then build from Android Studio
or `cd Apps && ./gradlew installDebug`.

> Note: `DEMO_PHONE` exists so the auto-call never reaches a real hospital during testing. Only leave it empty if you truly want to use the real hospital numbers from the database.

---

## 📁 Repo Structure

| Folder | Contents |
|---|---|
| `Apps/` | Android app (Kotlin + Jetpack Compose) |
| `API/` | Node.js backend (Express + Socket.IO + Gemini + MySQL) |
| `release/SIGAP.apk` | Ready-to-install APK |

---

Built for **GH 7.0**.
