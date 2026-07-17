package com.example.siaga.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.siaga.MainActivity
import com.example.siaga.R

/**
 * Widget SOS: satu tap dari home screen membuka aplikasi dengan mikrofon
 * langsung menyala.
 *
 * Kenapa tidak merekam tanpa membuka aplikasi: sejak Android 11, foreground
 * service yang dijalankan saat aplikasi ada di background TIDAK mendapat akses
 * mikrofon (aturan "while-in-use"). Lagipula alur darurat berakhir di panggilan
 * telepon dan user harus melihat instruksi P3K dari AI — aplikasinya tetap
 * harus muncul beberapa detik kemudian.
 *
 * Kenapa hanya tap, bukan tahan: long-press pada widget selalu dirampas
 * launcher untuk memindahkan/menghapus widget, tidak bisa di-override. Jadi
 * lewat widget modelnya "tap lalu bicara" — mic berhenti sendiri saat user diam.
 */
class SosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                SosWidgetContent()
            }
        }
    }
}

@Composable
private fun SosWidgetContent() {
    val context = LocalContext.current
    val launchIntent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .putExtra(MainActivity.EXTRA_START_LISTENING, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    // Efek kaca dari Figma tidak bisa ditiru: widget digambar oleh launcher dan
    // tidak bisa memburamkan wallpaper di belakangnya. Yang paling mendekati:
    // latar gelap semi-transparan dengan radius yang sama.
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(25.dp)
            .background(WidgetBackground)
            .padding(horizontal = 14.dp)
            .clickable(actionStartActivity(launchIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.logo_siaga),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
        )

        Spacer(GlanceModifier.width(10.dp))

        Text(
            text = "S!GAP siap membantu…",
            style = TextStyle(
                color = ColorProvider(WidgetTextColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )

        Spacer(GlanceModifier.width(10.dp))

        // Pakai mic_badge (vektor) — bukan hasil ekspor node tombolnya. Figma
        // mengekspor node beserta latar frame induknya, jadi PNG-nya opak dan
        // lingkarannya tampil sebagai kotak abu-abu. mic_badge isinya path asli
        // yang sama, tanpa latar, dan tajam di ukuran berapa pun.
        Image(
            provider = ImageProvider(R.drawable.mic_badge),
            contentDescription = "Lapor darurat",
            modifier = GlanceModifier.size(50.dp),
        )
    }
}

private val WidgetBackground = Color(0xE60D0710)
private val WidgetTextColor = Color(0xFFF0F0F0)

class SosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SosWidget()
}
