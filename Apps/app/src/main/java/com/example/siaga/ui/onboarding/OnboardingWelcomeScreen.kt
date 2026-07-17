package com.example.siaga.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siaga.R
import com.example.siaga.ui.components.SiagaBackdrop
import com.example.siaga.ui.theme.SIAGATheme
import com.example.siaga.ui.theme.SiagaRed
import com.example.siaga.ui.theme.SiagaTextMuted
import com.example.siaga.ui.theme.SiagaTextPrimary

/** Tinggi frame Figma "SIAGA - 02 Onboarding Welcome". */
private const val DESIGN_HEIGHT = 891.61f

private val GlassFill = Color(0x14FFFFFF) // putih 8%
private val GlassBorder = Color(0x2EFFFFFF) // putih 18%
private val RingOuter = Color(0xFF1E1E1E)
private val RingInner = Color(0xFF2A1010)
private val DotIdle = Color(0xFF444444)

/**
 * SIAGA - 02 Onboarding Welcome.
 *
 * Halaman perkenalan, hanya muncul saat aplikasi pertama kali dibuka.
 *
 * @param onStart tombol "Mulai" — lanjut ke halaman izin (SIAGA-03).
 * @param onSkip "Lewati" — langsung ke layar darurat.
 */
@Composable
fun OnboardingWelcomeScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SiagaBackdrop(designHeight = DESIGN_HEIGHT, modifier = modifier) {
        val screenHeight = maxHeight

        Text(
            text = "Lewati",
            fontWeight = FontWeight.Normal,
            fontSize = 13.733.sp,
            color = SiagaTextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = (-25.354).dp,
                    y = designY(59.159f, screenHeight),
                )
                .clickable(onClick = onSkip),
        )

        // Bobot spacer = sisa ruang di atas & bawah konten pada frame Figma.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(179.590f))

            MicEmblem()

            Spacer(Modifier.height(25.354.dp))

            Text(
                text = "Bantuan secepat\nucapanmu.",
                fontWeight = FontWeight.Bold,
                fontSize = 27.467.sp,
                lineHeight = 32.96.sp, // 1.2 x ukuran font
                color = SiagaTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.79.dp))

            Text(
                text = "SIGAP menghubungkanmu ke faskes terdekat hanya dengan " +
                    "suara — cukup tekan dan bicara.",
                fontWeight = FontWeight.Normal,
                fontSize = 13.733.sp,
                lineHeight = 19.91.sp, // 1.45 x ukuran font
                color = SiagaTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(327.487.dp),
            )

            Spacer(Modifier.height(31.692.dp))

            PageIndicator()

            Spacer(Modifier.height(40.144.dp))

            Button(
                onClick = onStart,
                shape = RoundedCornerShape(16.903.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SiagaRed),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .padding(horizontal = 25.354.dp)
                    .fillMaxWidth()
                    .height(59.159.dp),
            ) {
                Text(
                    text = "Mulai",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.903.sp,
                    color = SiagaTextPrimary,
                )
            }

            Spacer(Modifier.height(19.015.dp))

            Text(
                text = "Sudah pakai SIGAP? Masuk",
                fontWeight = FontWeight.Normal,
                fontSize = 12.677.sp,
                color = SiagaTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(50.71f))
        }
    }
}

/**
 * Lencana mic dengan cakram kaca dan dua cincin. Semuanya sepusat, jadi
 * cukup ditumpuk di tengah satu kotak seukuran cincin terluar.
 *
 * Catatan: di Figma cakramnya pakai backdrop blur. Yang ada di belakangnya
 * cuma gradient dan orb yang sudah buram, jadi blur itu tidak mengubah apa pun
 * secara kasatmata dan sengaja tidak ditiru (Compose tidak punya backdrop blur
 * bawaan).
 */
@Composable
private fun MicEmblem() {
    Box(
        modifier = Modifier.size(312.697.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Cakram kaca (49:5)
        Box(
            modifier = Modifier
                .size(274.667.dp)
                .shadow(25.354.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(GlassFill, CircleShape)
                .border(1.056.dp, GlassBorder, CircleShape)
        )
        // Cincin luar (28:3)
        Box(
            modifier = Modifier
                .size(312.697.dp)
                .border(1.585.dp, RingOuter, CircleShape)
        )
        // Cincin dalam (28:4)
        Box(
            modifier = Modifier
                .size(249.313.dp)
                .border(1.056.dp, RingInner, CircleShape)
        )
        Image(
            painter = painterResource(R.drawable.mic_badge),
            contentDescription = null,
            modifier = Modifier.size(190.154.dp),
        )
    }
}

/** Titik halaman onboarding: halaman pertama aktif. */
@Composable
private fun PageIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.338.dp),
    ) {
        Box(Modifier.size(8.451.dp).background(SiagaRed, CircleShape))
        Box(Modifier.size(6.338.dp).background(DotIdle, CircleShape))
        Box(Modifier.size(6.338.dp).background(DotIdle, CircleShape))
    }
}

/** Ubah koordinat Y frame Figma jadi posisi di layar sungguhan. */
private fun designY(y: Float, screenHeight: Dp): Dp = screenHeight * (y / DESIGN_HEIGHT)

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun OnboardingWelcomePreview() {
    SIAGATheme {
        OnboardingWelcomeScreen(onStart = {}, onSkip = {})
    }
}
