package com.example.siaga.ui.faskes

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.example.siaga.data.HospitalRepository
import com.example.siaga.data.NearbyHospital
import com.example.siaga.data.location.LocationClient
import com.example.siaga.ui.components.SiagaBackdrop
import com.example.siaga.ui.components.SiagaGlassCard
import com.example.siaga.ui.components.SiagaPageHeader
import com.example.siaga.ui.components.SiagaTextDim
import com.example.siaga.ui.components.SiagaTextNavIdle
import com.example.siaga.ui.theme.SiagaRed
import com.example.siaga.ui.theme.SiagaTextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tinggi frame desain, mengikuti layar SIAGA lainnya. */
private const val DESIGN_HEIGHT = 891.61f

data class FaskesUiState(
    val hospitals: List<NearbyHospital> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class FaskesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HospitalRepository()
    private val locationClient = LocationClient(application)

    private val _uiState = MutableStateFlow(FaskesUiState())
    val uiState: StateFlow<FaskesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = FaskesUiState(isLoading = true)
        viewModelScope.launch {
            // Lokasi cuma untuk mengurutkan; kalau belum diizinkan, daftarnya
            // tetap tampil (tanpa jarak) — jangan sampai halaman jadi kosong.
            val location = runCatching {
                if (hasLocationPermission()) locationClient.getCurrentLocation() else null
            }.getOrNull()

            runCatching { repository.fetchNearby(location?.latitude, location?.longitude) }
                .onSuccess { _uiState.value = FaskesUiState(hospitals = it, isLoading = false) }
                .onFailure {
                    _uiState.value = FaskesUiState(
                        isLoading = false,
                        error = "Gagal memuat daftar faskes. Periksa koneksi lalu coba lagi.",
                    )
                }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Halaman Faskes: daftar rumah sakit terdekat.
 *
 * Tidak ada frame Figma untuk halaman ini, jadi gayanya mengikuti sistem yang
 * sudah ada — backdrop yang sama, header seperti SIAGA-03, dan kartu kaca.
 */
@Composable
fun FaskesScreen(
    onCall: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: FaskesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SiagaBackdrop(designHeight = DESIGN_HEIGHT) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(116.205.dp))

            Box(Modifier.padding(horizontal = 25.354.dp)) {
                SiagaPageHeader(
                    title = "Faskes terdekat",
                    subtitle = "Rumah sakit di sekitar lokasimu.",
                )
            }

            Spacer(Modifier.height(21.128.dp))

            when {
                uiState.isLoading -> CenterMessage {
                    CircularProgressIndicator(color = SiagaRed, strokeWidth = 2.dp)
                }

                uiState.error != null -> CenterMessage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error!!,
                            fontSize = 13.733.sp,
                            color = SiagaTextDim,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.677.dp))
                        Button(
                            onClick = viewModel::load,
                            shape = RoundedCornerShape(10.564.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SiagaRed),
                        ) {
                            Text("Coba lagi", fontSize = 12.677.sp, color = SiagaTextPrimary)
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 25.354.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.902.dp),
                ) {
                    items(uiState.hospitals) { hospital ->
                        HospitalCard(hospital, onCall)
                    }
                }
            }

            bottomBar()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CenterMessage(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 25.354.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun HospitalCard(hospital: NearbyHospital, onCall: (String) -> Unit) {
    SiagaGlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hospital.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.79.sp,
                    color = SiagaTextPrimary,
                )
                Spacer(Modifier.height(4.226.dp))
                Text(
                    text = hospital.address,
                    fontSize = 12.677.sp,
                    lineHeight = 17.dp.value.sp,
                    color = SiagaTextNavIdle,
                )
            }
            hospital.distanceKm?.let { km ->
                Spacer(Modifier.size(8.451.dp))
                Text(
                    text = if (km < 10) "%.1f km".format(km) else "%.0f km".format(km),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.677.sp,
                    color = SiagaRed,
                )
            }
        }

        Spacer(Modifier.height(12.677.dp))

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
