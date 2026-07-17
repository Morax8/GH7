package com.example.siaga.ui.riwayat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.example.siaga.data.Incident
import com.example.siaga.data.IncidentHistory
import com.example.siaga.data.model.EmergencyState
import com.example.siaga.ui.components.SiagaBackdrop
import com.example.siaga.ui.components.SiagaGlassCard
import com.example.siaga.ui.components.SiagaPageHeader
import com.example.siaga.ui.components.SiagaTextDim
import com.example.siaga.ui.components.SiagaTextNavIdle
import com.example.siaga.ui.theme.SiagaRed
import com.example.siaga.ui.theme.SiagaTextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tinggi frame desain, mengikuti layar SIAGA lainnya. */
private const val DESIGN_HEIGHT = 891.61f

/**
 * Halaman Riwayat: laporan darurat yang pernah dikirim, tersimpan di HP.
 *
 * Tidak ada frame Figma untuk halaman ini, jadi gayanya mengikuti sistem yang
 * sudah ada — backdrop yang sama, header seperti SIAGA-03, dan kartu kaca.
 */
@Composable
fun RiwayatScreen(bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val history = remember { IncidentHistory(context) }
    val incidents by remember { history.incidents }.collectAsState(initial = emptyList())

    SiagaBackdrop(designHeight = DESIGN_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(116.205.dp))

            Box(Modifier.padding(horizontal = 25.354.dp)) {
                SiagaPageHeader(
                    title = "Riwayat",
                    subtitle = "Laporan darurat yang pernah kamu kirim.",
                )
            }

            Spacer(Modifier.height(21.128.dp))

            if (incidents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 25.354.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Belum ada laporan.\nLaporan daruratmu akan tersimpan di sini.",
                        fontSize = 13.733.sp,
                        lineHeight = 19.91.sp,
                        color = SiagaTextDim,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 25.354.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.902.dp),
                ) {
                    items(incidents, key = { it.id }) { incident ->
                        IncidentCard(incident)
                    }
                }
            }

            bottomBar()
        }
    }
}

@Composable
private fun IncidentCard(incident: Incident) {
    val formatter = remember { SimpleDateFormat("d MMM yyyy • HH:mm", Locale("id", "ID")) }

    SiagaGlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatter.format(Date(incident.timestamp)),
                fontSize = 10.564.sp,
                color = SiagaTextNavIdle,
                modifier = Modifier.weight(1f),
            )
            if (incident.state == EmergencyState.CRITICAL) {
                Box(Modifier.size(6.338.dp).background(SiagaRed, CircleShape))
                Spacer(Modifier.size(6.338.dp))
                Text(
                    text = "KRITIS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.508.sp,
                    color = SiagaRed,
                )
            }
        }

        Spacer(Modifier.height(8.451.dp))

        Text(
            text = incident.report,
            fontWeight = FontWeight.Bold,
            fontSize = 13.733.sp,
            lineHeight = 19.91.sp,
            color = SiagaTextPrimary,
        )

        incident.instruction?.let { instruction ->
            Spacer(Modifier.height(6.338.dp))
            Text(
                text = instruction,
                fontSize = 12.677.sp,
                lineHeight = 18.dp.value.sp,
                color = SiagaTextNavIdle,
            )
        }

        incident.hospitalName?.let { name ->
            Spacer(Modifier.height(8.451.dp))
            Text(
                text = "Dirujuk ke $name",
                fontWeight = FontWeight.Bold,
                fontSize = 11.621.sp,
                color = SiagaRed,
            )
        }
    }
}
