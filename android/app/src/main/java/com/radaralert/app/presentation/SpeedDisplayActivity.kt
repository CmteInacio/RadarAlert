package com.radaralert.app.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.radaralert.app.domain.ProximityColor
import com.radaralert.app.domain.TipoAlerta
import com.radaralert.app.service.DisplayState
import com.radaralert.app.service.RadarForegroundService

private val BackgroundDark = Color(0xFF0B1220)
private val AccentCyan = Color(0xFF3FE0E0)
private val AccentAmber = Color(0xFFFFA733)
private val ProximityYellow = Color(0xFFF2C230)
private val ProximityOrange = Color(0xFFF28C30)
private val ProximityRed = Color(0xFFE23B3B)

class SpeedDisplayActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startForegroundServiceIfReady()
            requestBackgroundLocationIfNeeded()
        }
    }

    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* segundo plano é um extra; o serviço já roda com a localização em primeiro plano */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissionsThenStartService()

        setContent {
            val displayState by RadarForegroundService.displayState.collectAsState()
            RadarAlertScreen(displayState)
        }
    }

    private fun ensurePermissionsThenStartService() {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startForegroundServiceIfReady()
            requestBackgroundLocationIfNeeded()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun startForegroundServiceIfReady() {
        val intent = Intent(this, RadarForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

private fun labelFor(tipo: TipoAlerta): String = when (tipo) {
    TipoAlerta.RADAR_FIXO -> "Radar fixo"
    TipoAlerta.LOMBADA_ELETRONICA -> "Lombada eletrônica"
    TipoAlerta.POLICIA_RODOVIARIA -> "Polícia rodoviária"
    TipoAlerta.PEDAGIO -> "Pedágio"
}

private fun statusTextFor(displayState: DisplayState): String = when {
    displayState.syncingRadars -> "Sincronizando lista de radares..."
    !displayState.bluetoothConnected -> "Aguardando conexão com o ESP32..."
    else -> "Conectado ao ESP32"
}

@Composable
private fun RadarAlertScreen(displayState: DisplayState) {
    val radarState = displayState.radarState
    val context = LocalContext.current

    val backgroundColor by animateColorAsState(
        targetValue = when (radarState.color) {
            ProximityColor.NONE -> BackgroundDark
            ProximityColor.YELLOW -> ProximityYellow
            ProximityColor.ORANGE -> ProximityOrange
            ProximityColor.RED -> ProximityRed
        },
        label = "backgroundColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = "${radarState.speedKmh}",
                fontSize = 96.sp,
                color = AccentAmber
            )
            Text(text = "km/h", fontSize = 20.sp, color = AccentCyan)

            radarState.nearestRadar?.let { radar ->
                Text(
                    text = "Limite: ${radar.speedLimitKmh} km/h",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = labelFor(radar.tipo),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = statusTextFor(displayState),
                fontSize = 14.sp,
                color = AccentCyan,
                modifier = Modifier.padding(top = 32.dp)
            )
        }

        if (!displayState.bluetoothConnected) {
            BluetoothDisconnectedIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
        }

        IconButton(
            onClick = { context.startActivity(Intent(context, AlertTypeSettingsActivity::class.java)) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Configurações", tint = AccentCyan)
        }
    }
}

@Composable
private fun BluetoothDisconnectedIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "btBlink")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "btAlpha"
    )

    Icon(
        imageVector = Icons.Filled.Bluetooth,
        contentDescription = "Bluetooth desconectado",
        tint = ProximityRed.copy(alpha = alpha),
        modifier = modifier.padding(8.dp)
    )
}
