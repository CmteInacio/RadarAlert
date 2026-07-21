package com.radaralert.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionFilterTest {

    private val filter = DirectionFilter()

    private fun radar(sentido: SentidoTipo, direcao: Double) =
        RadarPoint(id = 1, latitude = -29.0, longitude = -51.0, speedLimitKmh = 60, sentidoTipo = sentido, direcaoGraus = direcao, tipo = TipoAlerta.RADAR_FIXO)

    @Test
    fun `unico is candidate only when heading matches direction`() {
        val radar = radar(SentidoTipo.UNICO, direcao = 0.0)
        assertTrue(filter.isCandidate(userBearing = 10.0, radar = radar))
        assertFalse(filter.isCandidate(userBearing = 170.0, radar = radar))
    }

    @Test
    fun `ambos is candidate for both the direction and its opposite, but not near-perpendicular travel`() {
        val radar = radar(SentidoTipo.AMBOS, direcao = 90.0)
        assertTrue(filter.isCandidate(userBearing = 95.0, radar = radar))
        assertTrue(filter.isCandidate(userBearing = 265.0, radar = radar))
        assertFalse(filter.isCandidate(userBearing = 180.0, radar = radar))
    }

    @Test
    fun `desconhecido is always a candidate`() {
        val radar = radar(SentidoTipo.DESCONHECIDO, direcao = 45.0)
        assertTrue(filter.isCandidate(userBearing = 200.0, radar = radar))
    }

    @Test
    fun `approach state flips to passed once radar is behind`() {
        val radar = RadarPoint(1, latitude = 0.0, longitude = 1.0, speedLimitKmh = 60, sentidoTipo = SentidoTipo.DESCONHECIDO, direcaoGraus = 0.0, tipo = TipoAlerta.RADAR_FIXO)

        // Usuário em (0,0) indo para leste (bearing 90) -> radar em (0,1) está à frente.
        assertEquals(
            RadarApproachState.APPROACHING,
            filter.approachState(userLat = 0.0, userLon = 0.0, userBearing = 90.0, radar = radar)
        )

        // Usuário em (0,2) ainda indo para leste -> radar ficou para trás.
        assertEquals(
            RadarApproachState.PASSED,
            filter.approachState(userLat = 0.0, userLon = 2.0, userBearing = 90.0, radar = radar)
        )
    }
}
