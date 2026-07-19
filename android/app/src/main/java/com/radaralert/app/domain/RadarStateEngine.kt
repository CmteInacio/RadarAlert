package com.radaralert.app.domain

/**
 * Ponto único que transforma leituras cruas de localização em [RadarState]:
 * suaviza velocidade, calcula rumo, filtra candidatos por sentido e decide
 * se o usuário está se aproximando de um radar (ou já cruzou).
 *
 * Uma vez que um radar entra em alerta, ele fica "travado" até ser cruzado
 * ou a distância voltar a passar de [resetRadiusMeters] — evita alternar
 * entre radares próximos por causa de pequenas variações de GPS.
 */
class RadarStateEngine(
    private val proximityCalculator: ProximityCalculator = ProximityCalculator(),
    private val directionFilter: DirectionFilter = DirectionFilter(),
    private val speedSmoother: SpeedSmoother = SpeedSmoother(),
    private val headingTracker: HeadingTracker = HeadingTracker(),
    private val yellowRadiusMeters: Double = 300.0,
    private val orangeRadiusMeters: Double = 200.0,
    private val redRadiusMeters: Double = 100.0,
    private val resetRadiusMeters: Double = 350.0
) {
    private var lockedRadarId: Long? = null

    fun onLocationUpdate(
        lat: Double,
        lon: Double,
        rawSpeedKmh: Double,
        sensorBearing: Float?,
        radars: List<RadarPoint>
    ): RadarState {
        val smoothedSpeed = speedSmoother.addAndSmooth(rawSpeedKmh)
        val bearing = headingTracker.update(lat, lon, sensorBearing)
            ?: return idleState(smoothedSpeed)

        lockedRadarId?.let { id ->
            val locked = radars.firstOrNull { it.id == id }
            if (locked == null) {
                lockedRadarId = null
            } else {
                val distance = GeoMath.distanceMeters(lat, lon, locked.latitude, locked.longitude)
                val state = directionFilter.approachState(lat, lon, bearing, locked)
                if (state == RadarApproachState.PASSED || distance > resetRadiusMeters) {
                    lockedRadarId = null
                } else {
                    return alertState(smoothedSpeed, locked, distance)
                }
            }
        }

        val candidates = radars.filter { directionFilter.isCandidate(bearing, it) }
        val nearest = proximityCalculator.findNearest(lat, lon, candidates) ?: return idleState(smoothedSpeed)

        if (nearest.distanceMeters > yellowRadiusMeters) return idleState(smoothedSpeed)

        val state = directionFilter.approachState(lat, lon, bearing, nearest.radar)
        if (state != RadarApproachState.APPROACHING) return idleState(smoothedSpeed)

        lockedRadarId = nearest.radar.id
        return alertState(smoothedSpeed, nearest.radar, nearest.distanceMeters)
    }

    private fun idleState(speedKmh: Double) = RadarState(
        speedKmh = speedKmh.toInt(),
        nearestRadar = null,
        distanceMeters = null,
        color = ProximityColor.NONE
    )

    private fun alertState(speedKmh: Double, radar: RadarPoint, distanceMeters: Double): RadarState {
        val color = when {
            distanceMeters <= redRadiusMeters -> ProximityColor.RED
            distanceMeters <= orangeRadiusMeters -> ProximityColor.ORANGE
            else -> ProximityColor.YELLOW
        }
        return RadarState(
            speedKmh = speedKmh.toInt(),
            nearestRadar = radar,
            distanceMeters = distanceMeters,
            color = color
        )
    }
}
