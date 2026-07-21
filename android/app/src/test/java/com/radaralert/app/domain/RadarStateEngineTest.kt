package com.radaralert.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarStateEngineTest {

    // Radar fixo na origem; usuário se desloca ao longo da longitude, indo
    // sempre para leste (bearing 90). Distâncias calculadas via Haversine:
    // -0.0050 -> ~556m, -0.0025 -> ~278m, -0.0005 -> ~56m, +0.0010 -> ~111m
    // (já do outro lado do radar).
    private val radar = RadarPoint(
        id = 1,
        latitude = 0.0,
        longitude = 0.0,
        speedLimitKmh = 60,
        sentidoTipo = SentidoTipo.DESCONHECIDO,
        direcaoGraus = 0.0,
        tipo = TipoAlerta.RADAR_FIXO
    )

    @Test
    fun `alerts by proximity band while approaching and clears after crossing`() {
        val engine = RadarStateEngine(speedSmoother = SpeedSmoother(windowSize = 1))

        var state = engine.onLocationUpdate(0.0, -0.0050, 80.0, sensorBearing = 90f, radars = listOf(radar))
        assertEquals(ProximityColor.NONE, state.color) // ~556m, fora do raio amarelo

        state = engine.onLocationUpdate(0.0, -0.0025, 80.0, sensorBearing = 90f, radars = listOf(radar))
        assertEquals(ProximityColor.YELLOW, state.color) // ~278m

        state = engine.onLocationUpdate(0.0, -0.0005, 80.0, sensorBearing = 90f, radars = listOf(radar))
        assertEquals(ProximityColor.RED, state.color) // ~56m

        state = engine.onLocationUpdate(0.0, 0.0010, 80.0, sensorBearing = 90f, radars = listOf(radar))
        assertEquals(ProximityColor.NONE, state.color) // cruzou o radar, mesmo a ~111m
    }

    @Test
    fun `ignores radar on opposite carriageway when sentido is unico`() {
        val engine = RadarStateEngine(speedSmoother = SpeedSmoother(windowSize = 1))
        // Radar cadastrado para quem vai para o oeste (270 graus), a ~111m a leste da origem.
        val oneWayRadar = radar.copy(longitude = 0.0010, sentidoTipo = SentidoTipo.UNICO, direcaoGraus = 270.0)

        // Usuário indo para leste (90 graus) -> pista contrária, não deve alertar.
        val state = engine.onLocationUpdate(0.0, 0.0, 80.0, sensorBearing = 90f, radars = listOf(oneWayRadar))
        assertEquals(ProximityColor.NONE, state.color)
    }
}
