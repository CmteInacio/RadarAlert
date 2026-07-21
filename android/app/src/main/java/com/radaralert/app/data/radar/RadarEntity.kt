package com.radaralert.app.data.radar

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.radaralert.app.domain.RadarPoint
import com.radaralert.app.domain.SentidoTipo
import com.radaralert.app.domain.TipoAlerta

@Entity(tableName = "radares")
data class RadarEntity(
    @PrimaryKey val id: Long,
    val latitude: Double,
    val longitude: Double,
    val velocidadeMaxima: Int,
    val sentidoTipo: String,
    val direcaoGraus: Double,
    val tipo: String
)

fun RadarEntity.toDomain() = RadarPoint(
    id = id,
    latitude = latitude,
    longitude = longitude,
    speedLimitKmh = velocidadeMaxima,
    sentidoTipo = SentidoTipo.valueOf(sentidoTipo),
    direcaoGraus = direcaoGraus,
    tipo = TipoAlerta.valueOf(tipo)
)
