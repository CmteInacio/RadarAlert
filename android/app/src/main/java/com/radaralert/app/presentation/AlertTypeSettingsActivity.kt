package com.radaralert.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radaralert.app.RadarAlertApp
import com.radaralert.app.domain.TipoAlerta
import com.radaralert.app.service.RadarForegroundService

private fun labelFor(tipo: TipoAlerta): String = when (tipo) {
    TipoAlerta.RADAR_FIXO -> "Radar fixo"
    TipoAlerta.LOMBADA_ELETRONICA -> "Lombada eletrônica"
    TipoAlerta.POLICIA_RODOVIARIA -> "Polícia rodoviária"
    TipoAlerta.PEDAGIO -> "Pedágio"
}

class AlertTypeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = (application as RadarAlertApp).alertaTypePrefs

        setContent {
            var enabled by remember { mutableStateOf(prefs.getEnabledTypes()) }

            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(text = "Tipos de alerta ativos")

                TipoAlerta.entries.forEach { tipo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = tipo in enabled,
                            onCheckedChange = { checked ->
                                enabled = if (checked) enabled + tipo else enabled - tipo
                            }
                        )
                        Text(text = labelFor(tipo))
                    }
                }

                Button(
                    modifier = Modifier.padding(top = 24.dp),
                    onClick = {
                        prefs.setEnabledTypes(enabled)
                        restartService()
                        finish()
                    }
                ) {
                    Text(text = "Aplicar e voltar")
                }
            }
        }
    }

    private fun restartService() {
        stopService(Intent(this, RadarForegroundService::class.java))
        startForegroundService(Intent(this, RadarForegroundService::class.java))
    }
}
