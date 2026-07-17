package com.example.siaga

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.siaga.data.OnboardingPreferences
import com.example.siaga.ui.chat.ChatScreen
import com.example.siaga.ui.chat.ChatViewModel
import com.example.siaga.ui.chat.ConnectionStatus
import com.example.siaga.ui.components.SiagaNavBar
import com.example.siaga.ui.components.SiagaTab
import com.example.siaga.ui.faskes.FaskesScreen
import com.example.siaga.ui.onboarding.OnboardingPermissionsScreen
import com.example.siaga.ui.onboarding.OnboardingWelcomeScreen
import com.example.siaga.ui.riwayat.RiwayatScreen
import com.example.siaga.ui.splash.SplashScreen
import com.example.siaga.ui.theme.SIAGATheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Wajib dipanggil sebelum setContent: menukar tema splash bawaan
        // (Theme.SIAGA.Splash) ke tema aplikasi begitu jendela siap digambar.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SIAGATheme {
                SIAGAApp(startListening = intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true)
            }
        }
    }

    companion object {
        /** Dikirim widget SOS: begitu layar darurat siap, langsung buka mic. */
        const val EXTRA_START_LISTENING = "start_listening"
    }
}

/** Halaman yang sedang ditampilkan. Onboarding dilewati kalau sudah pernah. */
private enum class Screen { SPLASH, ONBOARDING_WELCOME, ONBOARDING_PERMISSIONS, EMERGENCY }

@Composable
fun SIAGAApp(startListening: Boolean = false) {
    // ViewModel diambil di sini, bukan di dalam ChatScreen, supaya socket mulai
    // menyambung sejak splash tampil — waktunya kepakai, bukan sekadar menunggu.
    // Instansinya milik Activity, jadi ChatScreen nanti dapat yang sama.
    val chatViewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }

    fun finishOnboarding() {
        scope.launch { onboardingPrefs.setCompleted() }
        screen = Screen.EMERGENCY
    }

    when (screen) {
        Screen.SPLASH -> {
            SplashScreen()
            SplashGate(chatViewModel, onboardingPrefs) { alreadyOnboarded ->
                screen = if (alreadyOnboarded) Screen.EMERGENCY else Screen.ONBOARDING_WELCOME
            }
        }

        Screen.ONBOARDING_WELCOME -> OnboardingWelcomeScreen(
            onStart = { screen = Screen.ONBOARDING_PERMISSIONS },
            onSkip = ::finishOnboarding,
        )

        Screen.ONBOARDING_PERMISSIONS -> OnboardingPermissionsScreen(
            onDone = ::finishOnboarding,
        )

        // Nav bar-nya bagian dari desain tiap layar (SIAGA-05), jadi diberikan
        // sebagai slot, bukan dibungkus scaffold dari luar.
        Screen.EMERGENCY -> MainTabs(chatViewModel, startListening)
    }
}

/**
 * Menahan splash sampai socket tersambung, lalu memberi tahu ke mana harus
 * pindah. Dua pagar:
 * - [SPLASH_MIN_MS]: kalau server keburu jawab, splash tidak berkedip sekejap.
 * - [SPLASH_MAX_MS]: kalau server tidak terjangkau, aplikasi tetap jalan
 *   (layar utama sudah punya indikator "Terputus" + tombol coba lagi).
 */
@Composable
private fun SplashGate(
    viewModel: ChatViewModel,
    onboardingPrefs: OnboardingPreferences,
    onFinished: (alreadyOnboarded: Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val startedAt = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        val alreadyOnboarded = onboardingPrefs.isCompleted.first()
        withTimeoutOrNull(SPLASH_MAX_MS) {
            snapshotFlow { uiState.connectionStatus }
                .first { it == ConnectionStatus.CONNECTED }
        }
        val shown = System.currentTimeMillis() - startedAt
        if (shown < SPLASH_MIN_MS) delay(SPLASH_MIN_MS - shown)
        onFinished(alreadyOnboarded)
    }
}

/** Tiga halaman utama, berpindah lewat nav bar bawah. */
@Composable
private fun MainTabs(chatViewModel: ChatViewModel, startListening: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(SiagaTab.DARURAT) }
    val context = LocalContext.current

    val bottomBar: @Composable () -> Unit = {
        SiagaNavBar(selected = tab, onSelect = { tab = it })
    }

    fun openDialer(phoneNumber: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null))
        )
    }

    when (tab) {
        SiagaTab.DARURAT -> ChatScreen(chatViewModel, bottomBar, startListening)
        // Di halaman Faskes user yang memilih sendiri, jadi cukup buka dialer —
        // panggilan otomatis hanya untuk kondisi kritis di layar darurat.
        SiagaTab.FASKES -> FaskesScreen(onCall = ::openDialer, bottomBar = bottomBar)
        SiagaTab.RIWAYAT -> RiwayatScreen(bottomBar = bottomBar)
    }
}

private const val SPLASH_MIN_MS = 900L
private const val SPLASH_MAX_MS = 2_500L
