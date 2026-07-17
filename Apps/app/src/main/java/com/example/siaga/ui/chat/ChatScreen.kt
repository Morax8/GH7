package com.example.siaga.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.siaga.R
import com.example.siaga.data.model.EmergencyState
import com.example.siaga.data.model.Hospital
import com.example.siaga.ui.components.SiagaBackdrop
import com.example.siaga.ui.theme.SiagaRed
import com.example.siaga.ui.theme.SiagaTextPrimary
import kotlinx.coroutines.delay

/** Tinggi frame Figma "SIAGA - 05 SOS Recording (Active)". */
private const val DESIGN_HEIGHT = 891.61f

/** Label lokasi statis untuk demo di chip top bar. */
private const val LOCATION_LABEL = "Gading Serpong"

/**
 * true  = langsung menelepon saat kondisi kritis (butuh izin CALL_PHONE).
 * false = cuma membuka dialer yang sudah terisi nomor.
 *
 * Catatan: dengan true, setiap respon CRITICAL benar-benar melakukan panggilan.
 * Nomor tujuannya diatur di backend (DEMO_PHONE).
 */
private const val AUTO_CALL = true

private val GlassFill = Color(0x14FFFFFF) // putih 8%
private val GlassBorder = Color(0x2EFFFFFF) // putih 18%
private val RingOuterActive = Color(0x40C0392B) // merah 25%
private val RingInnerActive = Color(0x66C0392B) // merah 40%
private val RingIdle = Color(0x1FC0392B) // redup saat tidak merekam
private val ConnectedGreen = Color(0xFF22C55E)
private val TextDim = Color(0xFF555555)
private val TextDimmer = Color(0xFF444444)
private val TextNavIdle = Color(0xFF666666)

/**
 * SIAGA - 05, layar darurat utama.
 *
 * Figma cuma menggambarkan state "sedang merekam". State lainnya (diam,
 * menganalisis, jawaban AI, kondisi kritis + kartu RS) diturunkan dari bahasa
 * desain yang sama: judul & petunjuk berubah, cincin meredup saat tidak
 * merekam, dan kartu transkrip dipakai ulang untuk menampilkan jawaban AI.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    bottomBar: @Composable () -> Unit,
    startListening: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.onLocationPermissionGranted()
        else viewModel.onLocationPermissionDenied()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) viewModel.onMicPermissionDenied() }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun placeCall(phoneNumber: String) {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        if (AUTO_CALL && hasPermission(Manifest.permission.CALL_PHONE)) {
            // TelecomManager, bukan Intent.ACTION_CALL: kalau di HP ada lebih
            // dari satu aplikasi telepon, ACTION_CALL memunculkan dialog
            // "buka pakai aplikasi apa" dan panggilannya jadi tidak otomatis.
            try {
                context.getSystemService(TelecomManager::class.java)?.placeCall(uri, Bundle())
                return
            } catch (_: SecurityException) {
            }
        }
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }

    // Catatan: izin telepon TIDAK diminta di sini. Semua izin dikumpulkan di
    // layar onboarding (SIAGA-03), jauh sebelum darurat. Kalau user melewati
    // onboarding, panggilan otomatis turun jadi buka dialer (lihat placeCall).
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.PlaceCall -> placeCall(effect.phoneNumber)
                is ChatEffect.RequestLocation ->
                    if (locationPermissions.any(::hasPermission)) {
                        viewModel.onLocationPermissionGranted()
                    } else {
                        locationPermissionLauncher.launch(locationPermissions)
                    }
            }
        }
    }

    // Durasi rekaman untuk penghitung di kartu transkrip
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.isListening) {
        elapsedSeconds = 0
        while (uiState.isListening) {
            delay(1_000)
            elapsedSeconds++
        }
    }

    // Jalan mundur kalau mikrofon tidak bisa dipakai. Tidak ada di Figma, tapi
    // tanpa ini user yang menolak izin mikrofon tidak punya cara melapor.
    var showTypedInput by rememberSaveable { mutableStateOf(false) }

    // Dibuka dari widget SOS: mic langsung menyala tanpa perlu ditahan —
    // SpeechRecognizer berhenti sendiri saat user diam. rememberSaveable supaya
    // tidak menyala ulang saat layar diputar.
    var widgetLaunchHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startListening) {
        if (startListening && !widgetLaunchHandled) {
            widgetLaunchHandled = true
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                viewModel.startVoiceInput()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    SiagaBackdrop(designHeight = DESIGN_HEIGHT) {
        val screenHeight = maxHeight
        fun y(designY: Float) = screenHeight * (designY / DESIGN_HEIGHT)

        TopBar(uiState.connectionStatus, ::y, viewModel::retryConnection)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = y(173.251f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            StatusHeadline(uiState)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = y(287.344f)),
        ) {
            MicEmblem(
                isListening = uiState.isListening,
                onPressStart = {
                    if (hasPermission(Manifest.permission.RECORD_AUDIO)) viewModel.startVoiceInput()
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onPressEnd = viewModel::stopVoiceInput,
            )
        }

        if (uiState.isListening) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y(566.236f)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = "Ceritakan kondisi korban\ndengan jelas dan tenang",
                    fontSize = 12.677.sp,
                    color = TextDimmer,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.015.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = y(612.718f))
                .padding(horizontal = 21.128.dp),
        ) {
            TranscriptCard(
                uiState = uiState,
                elapsedSeconds = elapsedSeconds,
                onCall = ::placeCall,
                onToggleTypedInput = { showTypedInput = !showTypedInput },
            )
        }

        if (uiState.isListening) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y(703.569f)),
            ) {
                CancelButton(onClick = viewModel::stopVoiceInput)
            }
        }

        if (showTypedInput) {
            // Ditempel di atas keyboard, bukan di dalam kartu: tata letak layar
            // ini absolut mengikuti Figma, jadi kartunya tidak ikut terangkat
            // saat keyboard muncul dan input-nya bakal tertutup.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .background(Color(0xF2101010))
                    .padding(horizontal = 21.128.dp, vertical = 12.677.dp),
            ) {
                TypedInput(
                    onSend = {
                        viewModel.sendMessage(it)
                        showTypedInput = false
                    },
                )
            }
        } else {
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
        }

        uiState.crashCountdown?.let { seconds ->
            CrashCountdownOverlay(
                seconds = seconds,
                onCancel = viewModel::cancelCrashCountdown,
            )
        }
    }
}

/**
 * Muncul saat benturan keras terdeteksi.
 *
 * Sengaja menutup seluruh layar: aplikasi ini menelepon otomatis saat kritis,
 * jadi user harus benar-benar sadar dan punya kesempatan membatalkan sebelum
 * laporan terkirim.
 */
@Composable
private fun CrashCountdownOverlay(seconds: Int, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20D0710))
            // Telan semua sentuhan supaya tombol di bawahnya tidak tertekan
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 25.354.dp),
        ) {
            Text(
                text = "Benturan terdeteksi",
                fontWeight = FontWeight.Bold,
                fontSize = 27.467.sp,
                color = SiagaTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.677.dp))
            Text(
                text = "Laporan darurat dikirim otomatis dalam",
                fontSize = 13.733.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(21.128.dp))

            Box(
                modifier = Modifier
                    .size(126.769.dp)
                    .border(2.113.dp, SiagaRed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$seconds",
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.482.sp,
                    color = SiagaRed,
                )
            }

            Spacer(Modifier.height(29.579.dp))

            Box(
                modifier = Modifier
                    .size(width = 211.282.dp, height = 52.821.dp)
                    .background(Color(0x0FFFFFFF), RoundedCornerShape(26.41.dp))
                    .border(1.585.dp, GlassBorder, RoundedCornerShape(26.41.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Saya tidak apa-apa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.79.sp,
                    color = SiagaTextPrimary,
                )
            }
        }
    }
}

/** Status koneksi, nama aplikasi, dan chip lokasi. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TopBar(
    status: ConnectionStatus,
    y: (Float) -> androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit,
) {
    val (dotColor, label) = when (status) {
        ConnectionStatus.CONNECTED -> ConnectedGreen to "Terhubung"
        ConnectionStatus.CONNECTING -> Color(0xFFF9A825) to "Menghubungkanâ€¦"
        ConnectionStatus.DISCONNECTED -> SiagaRed to "Terputus"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-21.128).dp, y = y(47.538f))
            .clickable(enabled = status == ConnectionStatus.DISCONNECTED, onClick = onRetry),
    ) {
        Box(Modifier.size(8.451.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(6.338.dp))
        Text(label, fontSize = 11.621.sp, color = TextDim)
    }

    Text(
        text = "SIGAP",
        fontWeight = FontWeight.Bold,
        fontSize = 11.621.sp,
        color = TextDimmer,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = 25.354.dp, y = y(89.795f)),
    )

    // Chip lokasi. Di Figma chip ini kosong (cuma pin), teksnya belum diisi â€”
    // di sini diisi nama lokasi, karena itu jelas maksudnya.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-21.128).dp, y = y(84.513f))
            .height(27.467.dp)
            .background(GlassFill, RoundedCornerShape(13.733.dp))
            .border(1.056.dp, GlassBorder, RoundedCornerShape(13.733.dp))
            .padding(horizontal = 12.677.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_pin_small),
            contentDescription = null,
            modifier = Modifier.size(8.652.dp),
        )
        Spacer(Modifier.width(6.338.dp))
        Text(LOCATION_LABEL, fontSize = 10.564.sp, color = TextNavIdle)
    }
}

/** Judul besar + baris petunjuk, berubah mengikuti state. */
@Composable
private fun StatusHeadline(uiState: ChatUiState) {
    val (title, subtitle) = when {
        uiState.isListening -> "Kami sedang\nmendengarkan..." to "Jangan tutup aplikasi ini"
        uiState.isAnalyzing -> "AI sedang\nmenganalisis..." to "Mohon tunggu sebentar"
        else -> "Siap membantu\nkapan saja." to "Tahan tombol untuk melapor"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 27.467.sp,
            lineHeight = 33.805.sp,
            color = SiagaTextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.677.dp))
        Text(subtitle, fontSize = 13.733.sp, color = TextDim)
    }
}

/** Dua cincin sepusat + lencana mic. Ditahan untuk merekam. */
@Composable
private fun MicEmblem(
    isListening: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    val outer = if (isListening) RingOuterActive else RingIdle
    val inner = if (isListening) RingInnerActive else RingIdle

    Box(
        modifier = Modifier.size(312.697.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(312.697.dp).border(1.585.dp, outer, CircleShape))
        Box(Modifier.size(249.313.dp).border(1.056.dp, inner, CircleShape))

        Box(
            modifier = Modifier
                .size(211.282.dp) // Mic Hotspot (41:19) â€” area sentuh
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            try {
                                tryAwaitRelease()
                            } finally {
                                onPressEnd()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.mic_badge),
                contentDescription = "Tahan untuk berbicara",
                modifier = Modifier.size(190.154.dp),
            )
        }

        Text(
            text = if (isListening) "LEPASKAN" else "TAHAN",
            fontWeight = FontWeight.Bold,
            fontSize = 10.564.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.offset(y = 65.4.dp), // 509.19 - 443.69 (pusat lencana)
        )
    }
}

/**
 * Kartu transkrip. Saat merekam menampilkan ucapan yang sedang ditangkap;
 * selain itu menampilkan jawaban AI terakhir beserta kartu RS bila ada.
 */
@Composable
private fun TranscriptCard(
    uiState: ChatUiState,
    elapsedSeconds: Int,
    onCall: (String) -> Unit,
    onToggleTypedInput: () -> Unit,
) {
    val lastAi = uiState.messages.lastOrNull { it.sender == Sender.AI }
    val lastSystemError = uiState.messages.lastOrNull { it.isError }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassFill, RoundedCornerShape(14.79.dp))
            .border(1.056.dp, GlassBorder, RoundedCornerShape(14.79.dp))
            .padding(horizontal = 16.903.dp, vertical = 13.733.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TRANSKRIP",
                fontWeight = FontWeight.Bold,
                fontSize = 9.508.sp,
                color = TextDimmer,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isListening) {
                Box(Modifier.size(6.338.dp).background(SiagaRed, CircleShape))
                Spacer(Modifier.width(10.564.dp))
                Text(
                    text = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.621.sp,
                    color = SiagaRed,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard),
                    contentDescription = "Ketik manual",
                    tint = TextNavIdle,
                    modifier = Modifier
                        .size(16.903.dp)
                        .clickable(onClick = onToggleTypedInput),
                )
            }
        }

        Spacer(Modifier.height(8.451.dp))

        val body = when {
            uiState.isListening -> uiState.partialTranscript ?: "Mendengarkanâ€¦"
            lastSystemError != null && lastAi == null -> lastSystemError.text
            lastAi != null -> lastAi.text
            else -> "Tahan tombol merah dan ceritakan kondisi korban."
        }

        Column(
            modifier = Modifier
                .heightIn(max = 132.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (lastAi?.state == EmergencyState.CRITICAL && !uiState.isListening) {
                Text(
                    text = "KONDISI KRITIS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.508.sp,
                    color = SiagaRed,
                )
                Spacer(Modifier.height(4.226.dp))
            }
            Text(
                text = body,
                fontSize = 12.677.sp,
                lineHeight = 19.015.sp,
                color = if (uiState.isListening || lastAi != null) SiagaTextPrimary else TextDimmer,
            )
            lastAi?.hospital?.takeIf { !uiState.isListening }?.let { hospital ->
                Spacer(Modifier.height(10.564.dp))
                HospitalRow(hospital, onCall)
            }
        }

    }
}

@Composable
private fun HospitalRow(hospital: Hospital, onCall: (String) -> Unit) {
    Column {
        Text(
            text = hospital.name,
            fontWeight = FontWeight.Bold,
            fontSize = 12.677.sp,
            color = SiagaTextPrimary,
        )
        Text(
            text = "${hospital.address} â€¢ ${hospital.distance}",
            fontSize = 10.564.sp,
            color = TextNavIdle,
        )
        Spacer(Modifier.height(6.338.dp))
        Button(
            onClick = { onCall(hospital.phone) },
            shape = RoundedCornerShape(10.564.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SiagaRed),
            contentPadding = PaddingValues(horizontal = 12.677.dp),
            modifier = Modifier.height(33.805.dp),
        ) {
            Text(
                text = "Telepon ${hospital.phone}",
                fontWeight = FontWeight.Bold,
                fontSize = 11.621.sp,
                color = SiagaTextPrimary,
            )
        }
    }
}

@Composable
private fun TypedInput(onSend: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ketik keadaan daruratâ€¦", fontSize = 12.677.sp, color = TextNavIdle) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.677.sp, color = SiagaTextPrimary),
            singleLine = true,
            shape = RoundedCornerShape(10.564.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SiagaRed,
                unfocusedBorderColor = GlassBorder,
                cursorColor = SiagaRed,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.451.dp))
        Icon(
            painter = painterResource(R.drawable.ic_send),
            contentDescription = "Kirim",
            tint = SiagaRed,
            modifier = Modifier
                .size(21.128.dp)
                .clickable(enabled = input.isNotBlank()) { onSend(input); input = "" },
        )
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 158.462.dp, height = 46.482.dp)
            .background(Color(0x0FFFFFFF), RoundedCornerShape(23.241.dp))
            .border(1.585.dp, SiagaRed, RoundedCornerShape(23.241.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Batalkan", fontWeight = FontWeight.Bold, fontSize = 14.79.sp, color = SiagaRed)
    }
}
