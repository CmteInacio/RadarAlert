package com.radaralert.app.domain

data class NearestRadarResult(
    val radar: RadarPoint,
    val distanceMeters: Double
)

class ProximityCalculator {
    fun findNearest(currentLat: Double, currentLon: Double, radars: List<RadarPoint>): NearestRadarResult? {
        var best: RadarPoint? = null
        var bestDistance = Double.MAX_VALUE
        for (radar in radars) {
            val distance = GeoMath.distanceMeters(currentLat, currentLon, radar.latitude, radar.longitude)
            if (distance < bestDistance) {
                bestDistance = distance
                best = radar
            }
        }
        return best?.let { NearestRadarResult(it, bestDistance) }
    }
}
