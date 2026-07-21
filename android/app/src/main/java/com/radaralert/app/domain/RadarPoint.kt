package com.radaralert.app.domain

enum class SentidoTipo { UNICO, AMBOS, DESCONHECIDO }

enum class TipoAlerta { RADAR_FIXO, LOMBADA_ELETRONICA, POLICIA_RODOVIARIA, PEDAGIO }

data class RadarPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val speedLimitKmh: Int,
    val sentidoTipo: SentidoTipo,
    val direcaoGraus: Double,
    val tipo: TipoAlerta
)
