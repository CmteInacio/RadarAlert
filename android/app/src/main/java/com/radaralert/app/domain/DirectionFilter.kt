package com.radaralert.app.domain

enum class RadarApproachState { APPROACHING, PASSED }

/**
 * Decide se um radar é candidato a alerta considerando o sentido cadastrado
 * (campo DirType do formato iGO8/Amigo) e se o usuário já cruzou o ponto.
 */
class DirectionFilter(private val toleranceDegrees: Double = 80.0) {

    fun isCandidate(userBearing: Double, radar: RadarPoint): Boolean {
        return when (radar.sentidoTipo) {
            SentidoTipo.DESCONHECIDO -> true
            SentidoTipo.UNICO -> GeoMath.angleDifference(userBearing, radar.direcaoGraus) <= toleranceDegrees
            SentidoTipo.AMBOS -> {
                val opposite = (radar.direcaoGraus + 180) % 360
                GeoMath.angleDifference(userBearing, radar.direcaoGraus) <= toleranceDegrees ||
                    GeoMath.angleDifference(userBearing, opposite) <= toleranceDegrees
            }
        }
    }

    fun approachState(userLat: Double, userLon: Double, userBearing: Double, radar: RadarPoint): RadarApproachState {
        val bearingToRadar = GeoMath.bearingDegrees(userLat, userLon, radar.latitude, radar.longitude)
        val angle = GeoMath.angleDifference(userBearing, bearingToRadar)
        return if (angle <= 90.0) RadarApproachState.APPROACHING else RadarApproachState.PASSED
    }
}
