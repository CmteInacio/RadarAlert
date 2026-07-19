package com.radaralert.app.domain

/**
 * Mantém o rumo (bearing) atual do dispositivo. Prefere o bearing do próprio
 * sensor de GPS quando disponível (mais estável em velocidade); caso
 * contrário, deriva do deslocamento entre os dois últimos pontos, ignorando
 * deslocamentos pequenos demais (ruído de GPS parado/andando devagar).
 */
class HeadingTracker(private val minDisplacementMeters: Double = 3.0) {
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastBearing: Double? = null

    fun update(lat: Double, lon: Double, sensorBearing: Float?): Double? {
        if (sensorBearing != null) {
            lastBearing = sensorBearing.toDouble()
        } else {
            val prevLat = lastLat
            val prevLon = lastLon
            if (prevLat != null && prevLon != null) {
                val moved = GeoMath.distanceMeters(prevLat, prevLon, lat, lon)
                if (moved >= minDisplacementMeters) {
                    lastBearing = GeoMath.bearingDegrees(prevLat, prevLon, lat, lon)
                }
            }
        }
        lastLat = lat
        lastLon = lon
        return lastBearing
    }
}
