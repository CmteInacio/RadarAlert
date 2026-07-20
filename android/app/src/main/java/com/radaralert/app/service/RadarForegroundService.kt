package com.radaralert.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.radaralert.app.R
import com.radaralert.app.RadarAlertApp
import com.radaralert.app.data.bluetooth.BluetoothRepository
import com.radaralert.app.data.location.LocationRepository
import com.radaralert.app.domain.ProximityColor
import com.radaralert.app.domain.RadarPoint
import com.radaralert.app.domain.RadarState
import com.radaralert.app.domain.RadarStateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DisplayState(
    val radarState: RadarState,
    val bluetoothConnected: Boolean
)

class RadarForegroundService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var locationRepository: LocationRepository
    private lateinit var bluetoothRepository: BluetoothRepository
    private val stateEngine = RadarStateEngine()

    private var radars: List<RadarPoint> = emptyList()

    override fun onCreate() {
        super.onCreate()

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            stopSelf()
            return
        }

        val notification = buildNotification(getString(R.string.notification_text_idle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        locationRepository = LocationRepository(this)
        bluetoothRepository = BluetoothRepository(ESP32_MAC_ADDRESS, scope)
        bluetoothRepository.start()

        scope.launch {
            val app = application as RadarAlertApp
            app.radarRepository.syncIfNeeded()
            radars = app.radarRepository.getAllRadars()
        }

        scope.launch {
            combine(locationSamples(), bluetoothRepository.connected) { sample, btConnected -> sample to btConnected }
                .collect { (sample, btConnected) ->
                    val state = stateEngine.onLocationUpdate(
                        lat = sample.latitude,
                        lon = sample.longitude,
                        rawSpeedKmh = sample.speedKmh,
                        sensorBearing = sample.bearing,
                        radars = radars
                    )
                    publish(DisplayState(state, btConnected))
                }
        }
    }

    private fun locationSamples() = locationRepository.observeLocation()

    private fun publish(displayState: DisplayState) {
        _displayState.value = displayState
        bluetoothRepository.send(
            speedKmh = displayState.radarState.speedKmh,
            maxSpeedKmh = displayState.radarState.nearestRadar?.speedLimitKmh,
            distanceMeters = displayState.radarState.distanceMeters?.toInt(),
            state = displayState.radarState.color.name
        )
        updateNotification(displayState.radarState)
    }

    private fun updateNotification(state: RadarState) {
        val text = if (state.color == ProximityColor.NONE) {
            getString(R.string.notification_text_idle)
        } else {
            "Radar em ${state.distanceMeters?.toInt()}m — limite ${state.nearestRadar?.speedLimitKmh} km/h"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "radar_alert_service"
        private const val NOTIFICATION_ID = 1

        // TODO: mover para uma tela de configuração/pareamento em vez de fixo no código.
        private const val ESP32_MAC_ADDRESS = "4c:11:ae:f9:c3:a4"

        private val _displayState = MutableStateFlow(
            DisplayState(RadarState(0, null, null, ProximityColor.NONE), bluetoothConnected = false)
        )
        val displayState: StateFlow<DisplayState> = _displayState
    }
}
