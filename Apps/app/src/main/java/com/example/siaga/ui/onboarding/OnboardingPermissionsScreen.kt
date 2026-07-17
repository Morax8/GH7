package com.example.siaga.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/** Tinggi frame Figma "SIAGA - 03 Onboarding Permissions". */
private const val DESIGN_HEIGHT = 891.61f

private val CardFill = Color(0x14FFFFFF) // putih 8%
private val CardBorder = Color(0x2EFFFFFF) // putih 18%
private val IconCircleFill = Color(0x26C0392B) // merah 15%

/**
 * Izin yang diminta di layar ini.
 *
 * Semuanya diminta sekaligus di sini, jauh sebelum darurat terjadi — saat
 * kondisi kritis, tidak ada waktu buat user membaca dialog izin.
 */
private val REQUESTED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CALL_PHONE,
)

/**
 * SIAGA - 03 Onboarding Permissions.
 *
 * Halaman ini cuma menjelaskan; dialog izin sungguhan tetap dimunculkan sistem
 * satu per satu setelah tombol ditekan. Ditolak pun aplikasi tetap jalan —
 * tiap fitur punya jalan mundurnya sendiri (lihat ChatScreen).
 *
 * @param onDone dipanggil setelah izin selesai diproses, atau saat dilewati.
 */
@Composable
fun OnboardingPermissionsScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onDone() } // ditolak sebagian pun tetap lanjut

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
                .offset(x = (-25.354).dp, y = screenHeight * (59.159f / DESIGN_HEIGHT))
                .clickable(onClick = onDone),
        )

        // Bobot spacer = sisa ruang pada frame Figma.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.354.dp),
        ) {
            Spacer(Modifier.weight(116.205f))

            Text(
                text = "Izinkan akses\npenting",
                fontWeight = FontWeight.Bold,
                fontSize = 27.467.sp,
                color = SiagaTextPrimary,
            )

            Spacer(Modifier.height(14.79.dp))

            Text(
                text = "SIGAP butuh akses ini supaya bisa merespons secepat " +
                    "mungkin saat darurat.",
                fontWeight = FontWeight.Normal,
                fontSize = 13.733.sp,
                color = SiagaTextMuted,
            )

            Spacer(Modifier.height(21.128.dp))

            PermissionCard(
                iconRes = R.drawable.ic_perm_location,
                title = "Lokasi",
                description = "Membagikan posisimu ke faskes terdekat secara real-time.",
            )

            Spacer(Modifier.height(16.902.dp))

            PermissionCard(
                iconRes = R.drawable.ic_perm_mic,
                title = "Mikrofon",
                description = "Merekam suara untuk melaporkan kondisi darurat.",
            )

            Spacer(Modifier.height(16.902.dp))

            // Kartu ini belum ada di Figma — ditambahkan karena izinnya memang
            // diminta di sini. Tanpa kartunya, user melihat dialog izin telepon
            // tanpa pernah diberi tahu kenapa. Jarak & gayanya mengikuti dua
            // kartu di atasnya (jarak antar kartu 114.092 di frame Figma).
            PermissionCard(
                iconRes = R.drawable.ic_perm_phone,
                title = "Telepon",
                description = "Menghubungi faskes terdekat otomatis saat kondisi kritis.",
            )

            Spacer(Modifier.weight(152.124f))

            Button(
                onClick = { permissionLauncher.launch(REQUESTED_PERMISSIONS) },
                shape = RoundedCornerShape(16.903.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SiagaRed),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(59.159.dp),
            ) {
                Text(
                    text = "Izinkan & Lanjutkan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.903.sp,
                    color = SiagaTextPrimary,
                )
            }

            Spacer(Modifier.height(19.015.dp))

            Text(
                text = "Nanti saja",
                fontWeight = FontWeight.Normal,
                fontSize = 12.677.sp,
                color = SiagaTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDone),
            )

            Spacer(Modifier.weight(50.707f))
        }
    }
}

/**
 * Kartu penjelasan satu izin.
 *
 * Catatan: di Figma kartunya pakai backdrop blur. Yang ada di belakangnya cuma
 * gradient dan orb yang sudah buram, jadi blur itu tidak mengubah apa pun
 * secara kasatmata dan sengaja tidak ditiru (Compose tidak punya backdrop blur
 * bawaan).
 */
@Composable
private fun PermissionCard(
    iconRes: Int,
    title: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .height(97.19.dp)
            .shadow(19.015.dp, RoundedCornerShape(16.903.dp), spotColor = Color.Black)
            .background(CardFill, RoundedCornerShape(16.903.dp))
            .border(1.056.dp, CardBorder, RoundedCornerShape(16.903.dp))
            .padding(start = 16.903.dp, top = 25.354.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.482.dp)
                .background(IconCircleFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(21.128.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 16.902.dp, top = 2.113.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.79.sp,
                color = SiagaTextPrimary,
            )
            Spacer(Modifier.height(4.226.dp))
            Text(
                text = description,
                fontWeight = FontWeight.Normal,
                fontSize = 12.677.sp,
                color = SiagaTextMuted,
                modifier = Modifier.width(264.103.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun OnboardingPermissionsPreview() {
    SIAGATheme {
        OnboardingPermissionsScreen(onDone = {})
    }
}
