package com.radaralert.app.domain

enum class SentidoTipo { UNICO, AMBOS, DESCONHECIDO }

data class RadarPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val speedLimitKmh: Int,
    val sentidoTipo: SentidoTipo,
    val direcaoGraus: Double
)
