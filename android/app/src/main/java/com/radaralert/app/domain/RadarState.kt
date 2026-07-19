package com.radaralert.app.domain

enum class ProximityColor { NONE, YELLOW, ORANGE, RED }

data class RadarState(
    val speedKmh: Int,
    val nearestRadar: RadarPoint?,
    val distanceMeters: Double?,
    val color: ProximityColor
)
