package com.radaralert.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.radaralert.app.domain.GpsIntervalStrategy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val bearing: Float?
)

/**
 * Emite atualizações de localização, reajustando o intervalo de consulta do
 * GPS (via [GpsIntervalStrategy]) conforme a última velocidade observada.
 */
class LocationRepository(
    context: Context,
    private val intervalStrategy: GpsIntervalStrategy = GpsIntervalStrategy()
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<LocationSample> = callbackFlow {
        var currentIntervalMillis = -1L
        lateinit var callback: LocationCallback

        fun requestUpdates(intervalMillis: Long) {
            currentIntervalMillis = intervalMillis
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speedKmh = location.speed * 3.6
                val sample = LocationSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedKmh = speedKmh,
                    bearing = if (location.hasBearing()) location.bearing else null
                )
                trySend(sample)

                val desiredInterval = intervalStrategy.intervalFor(speedKmh)
                if (desiredInterval != currentIntervalMillis) {
                    client.removeLocationUpdates(this)
                    requestUpdates(desiredInterval)
                }
            }
        }

        requestUpdates(intervalStrategy.intervalFor(0.0))

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
