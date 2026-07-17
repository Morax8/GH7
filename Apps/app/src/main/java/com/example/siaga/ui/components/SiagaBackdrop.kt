package com.example.siaga.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.siaga.R
import com.example.siaga.ui.theme.Inter
import com.example.siaga.ui.theme.SiagaBackgroundEnd
import com.example.siaga.ui.theme.SiagaBackgroundStart

/**
 * Gaya teks dasar layar SIAGA.
 *
 * Perlu disetel eksplisit karena Material3 mewariskan `typography.bodyLarge`
 * ke setiap Text — dan template bawaan Android Studio mengisinya dengan
 * letterSpacing 0.5sp serta lineHeight 24sp. Keduanya diam-diam merusak desain:
 * teks jadi melebar (wrap lebih cepat) dan judul besar terjepit. Figma memakai
 * Inter tanpa letter spacing dan line height bawaan font.
 */
private val SiagaTextStyle = TextStyle(
    fontFamily = Inter,
    letterSpacing = 0.sp,
)

/** Lebar frame Figma. Semua layar SIAGA dirancang di lebar ini. */
const val DESIGN_WIDTH = 412f

/**
 * Latar yang dipakai semua layar SIAGA: gradient gelap + tiga orb cahaya.
 * Sama persis di frame Splash maupun Onboarding, jadi asetnya dipakai bersama.
 *
 * @param designHeight tinggi frame Figma layar ini — tiap frame beda
 *   (Splash 917, Onboarding 891.61), dan angka ini yang dipakai untuk
 *   mengubah koordinat Figma jadi proporsi layar sungguhan.
 */
@Composable
fun SiagaBackdrop(
    designHeight: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val background = with(density) {
            Brush.linearGradient(
                colors = listOf(SiagaBackgroundStart, SiagaBackgroundEnd),
                start = Offset(
                    x = maxWidth.toPx() * 0.040f,
                    y = maxHeight.toPx() * 0.220f,
                ),
                end = Offset(
                    x = maxWidth.toPx() * 1.257f,
                    y = maxHeight.toPx() * 0.557f,
                ),
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(background))

        // Orb-nya diekspor sudah terpotong mengikuti frame, jadi tiap gambar
        // dipasang persis di area asalnya (bukan di titik tengah ellipse-nya).
        Orb(R.drawable.splash_orb_1, 0f, 0f, 412f, 401.43f, maxWidth, maxHeight, designHeight)
        Orb(R.drawable.splash_orb_2, 0f, 264.10f, 327.49f, 570.47f, maxWidth, maxHeight, designHeight)
        Orb(R.drawable.splash_orb_3, 63.38f, 528.20f, 348.62f, 388.80f, maxWidth, maxHeight, designHeight)

        CompositionLocalProvider(LocalTextStyle provides SiagaTextStyle) {
            content()
        }
    }
}

/** Cahaya latar buram. Ukuran & posisinya proporsional terhadap frame desain. */
@Composable
private fun BoxScope.Orb(
    drawableRes: Int,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    screenWidth: Dp,
    screenHeight: Dp,
    designHeight: Float,
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset(
                x = screenWidth * (x / DESIGN_WIDTH),
                y = screenHeight * (y / designHeight),
            )
            .size(
                width = screenWidth * (width / DESIGN_WIDTH),
                height = screenHeight * (height / designHeight),
            ),
    )
}
