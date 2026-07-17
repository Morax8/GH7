package com.example.siaga.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siaga.R
import com.example.siaga.ui.components.SiagaBackdrop
import com.example.siaga.ui.theme.SIAGATheme
import com.example.siaga.ui.theme.SiagaTextMuted
import com.example.siaga.ui.theme.SiagaTextPrimary

/** Tinggi frame Figma "SIAGA - 01 Splash". */
private const val DESIGN_HEIGHT = 917f

/**
 * SIAGA - 01 Splash.
 *
 * Layar ini tidak menahan apa pun sendiri — pemanggilnya yang menentukan kapan
 * pindah (lihat [com.example.siaga.MainActivity]).
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    SiagaBackdrop(designHeight = DESIGN_HEIGHT, modifier = modifier) {
        // Bobot spacer = sisa ruang di atas & bawah konten pada frame Figma.
        // Di layar setinggi 917dp hasilnya sama persis dengan desain; di layar
        // lain proporsinya tetap terjaga.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(282.488f))

            // Di frame splash lencananya sedikit diregangkan vertikal
            // (190.154 x 195.569), makanya tingginya beda dari lebarnya.
            Image(
                painter = painterResource(R.drawable.mic_badge),
                contentDescription = null,
                modifier = Modifier.size(width = 190.154.dp, height = 195.569.dp),
            )

            Spacer(Modifier.height(20.57.dp))

            Text(
                text = "SIGAP",
                fontWeight = FontWeight.Bold,
                fontSize = 38.031.sp,
                letterSpacing = 1.5212.sp,
                color = SiagaTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.93.dp))

            Text(
                text = "Respons darurat dalam sekali sentuh.",
                fontWeight = FontWeight.Normal,
                fontSize = 13.733.sp,
                color = SiagaTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(344.426f))
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SplashScreenPreview() {
    SIAGATheme {
        SplashScreen()
    }
}
