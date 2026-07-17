package com.example.siaga.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siaga.R
import com.example.siaga.ui.theme.SiagaRed
import com.example.siaga.ui.theme.SiagaTextPrimary

// Gaya kaca dari desain Figma, dipakai semua kartu & panel.
val GlassFill = Color(0x14FFFFFF) // putih 8%
val GlassBorder = Color(0x2EFFFFFF) // putih 18%
val SiagaTextDim = Color(0xFF555555)
val SiagaTextDimmer = Color(0xFF444444)
val SiagaTextNavIdle = Color(0xFF666666)

/** Halaman yang bisa dipilih lewat nav bar bawah. */
enum class SiagaTab(val label: String, val icon: Int) {
    DARURAT("Darurat", R.drawable.ic_nav_darurat),
    FASKES("Faskes", R.drawable.ic_nav_faskes),
    RIWAYAT("Riwayat", R.drawable.ic_nav_riwayat),
}

/**
 * Nav bar bawah dari desain SIAGA-05.
 *
 * Item "Respon" yang ada di Figma sengaja dibuang — itu pintu ke dashboard
 * responder (frame 07–11), persona terpisah yang di luar lingkup.
 */
@Composable
fun SiagaNavBar(
    selected: SiagaTab,
    onSelect: (SiagaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(95.077.dp)
            .background(GlassFill, RoundedCornerShape(topStart = 25.354.dp, topEnd = 25.354.dp))
            .border(
                1.056.dp,
                GlassBorder,
                RoundedCornerShape(topStart = 25.354.dp, topEnd = 25.354.dp),
            )
            .padding(top = 12.677.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SiagaTab.entries.forEach { tab ->
            NavItem(tab, selected == tab) { onSelect(tab) }
        }
    }
}

@Composable
private fun NavItem(tab: SiagaTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Image(
                painter = painterResource(tab.icon),
                contentDescription = tab.label,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (selected) SiagaTextPrimary else SiagaTextNavIdle
                ),
                modifier = Modifier.size(23.241.dp),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .offset(y = (-9.5).dp)
                        .size(4.226.dp)
                        .background(SiagaRed, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(9.508.dp))
        Text(
            text = tab.label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 9.508.sp,
            color = if (selected) SiagaTextPrimary else SiagaTextNavIdle,
        )
    }
}

/** Judul halaman, mengikuti gaya header SIAGA-03. */
@Composable
fun SiagaPageHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 27.467.sp,
            color = SiagaTextPrimary,
        )
        Spacer(Modifier.height(8.451.dp))
        Text(text = subtitle, fontSize = 13.733.sp, color = SiagaTextDim)
    }
}

/** Kartu kaca, gaya sama dengan kartu izin SIAGA-03. */
@Composable
fun SiagaGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassFill, RoundedCornerShape(16.903.dp))
            .border(1.056.dp, GlassBorder, RoundedCornerShape(16.903.dp))
            .padding(16.903.dp),
        content = content,
    )
}
