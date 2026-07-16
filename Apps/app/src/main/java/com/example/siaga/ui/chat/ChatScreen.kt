package com.example.siaga.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.siaga.R
import com.example.siaga.data.model.EmergencyState
import com.example.siaga.data.model.Hospital

/**
 * Layar chat darurat: bubble user vs AI, indikator analisis, tampilan khusus
 * state CRITICAL + kartu rumah sakit, dan indikator status koneksi di top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.onLocationPermissionGranted()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    fun openDialer(phoneNumber: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.OpenDialer -> openDialer(effect.phoneNumber)

                is ChatEffect.RequestLocation -> {
                    val alreadyGranted = locationPermissions.any {
                        ContextCompat.checkSelfPermission(context, it) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (alreadyGranted) {
                        viewModel.onLocationPermissionGranted()
                    } else {
                        permissionLauncher.launch(locationPermissions)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                status = uiState.connectionStatus,
                onRetry = viewModel::retryConnection,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            MessageList(
                messages = uiState.messages,
                isAnalyzing = uiState.isAnalyzing,
                onCallHospital = ::openDialer,
                modifier = Modifier.weight(1f),
            )
            HorizontalDivider()
            MessageInput(
                enabled = !uiState.isAnalyzing,
                onSend = viewModel::sendMessage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(status: ConnectionStatus, onRetry: () -> Unit) {
    val (statusColor, statusText) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF2E7D32) to "Terhubung"
        ConnectionStatus.CONNECTING -> Color(0xFFF9A825) to "Menghubungkan…"
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.error to "Terputus"
    }

    TopAppBar(
        title = {
            Column {
                Text("SIAGA — Dispatcher Darurat", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        actions = {
            if (status == ConnectionStatus.DISCONNECTED) {
                IconButton(onClick = onRetry) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = "Coba hubungkan ulang"
                    )
                }
            }
        },
    )
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isAnalyzing: Boolean,
    onCallHospital: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll ke bawah setiap ada pesan baru / indikator analisis muncul
    LaunchedEffect(messages.size, isAnalyzing) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messages.isEmpty()) {
            item(key = "empty-hint") {
                Text(
                    text = "Jelaskan keadaan darurat Anda, misalnya:\n" +
                        "\"Tolong, ayah saya tidak sadarkan diri\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                )
            }
        }
        items(messages, key = { it.id }) { message ->
            when (message.sender) {
                Sender.USER -> UserBubble(message)
                Sender.AI -> AiBubble(message, onCallHospital)
                Sender.SYSTEM -> SystemMessage(message)
            }
        }
        if (isAnalyzing) {
            item(key = "analyzing") { AnalyzingIndicator() }
        }
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AiBubble(message: ChatMessage, onCallHospital: (String) -> Unit) {
    val isCritical = message.state == EmergencyState.CRITICAL

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = if (isCritical) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isCritical) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "KONDISI KRITIS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCritical) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                message.hospital?.let { hospital ->
                    Spacer(Modifier.height(10.dp))
                    HospitalCard(hospital, onCallHospital)
                }
            }
        }
    }
}

@Composable
private fun HospitalCard(hospital: Hospital, onCall: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Rumah Sakit Terdekat",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hospital.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (hospital.address.isNotBlank()) {
                Text(hospital.address, style = MaterialTheme.typography.bodySmall)
            }
            if (hospital.distance.isNotBlank()) {
                Text(
                    text = "Jarak: ${hospital.distance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onCall(hospital.phone) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_call),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Telepon ${hospital.phone}")
            }
        }
    }
}

@Composable
private fun SystemMessage(message: ChatMessage) {
    Text(
        text = message.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (message.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outline
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun AnalyzingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "AI sedang menganalisis…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun MessageInput(enabled: Boolean, onSend: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ketik keadaan darurat Anda…") },
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                onSend(input)
                input = ""
            },
            enabled = enabled && input.isNotBlank(),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = "Kirim pesan")
        }
    }
}
