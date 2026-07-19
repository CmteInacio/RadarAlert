package com.radaralert.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {

    @Test
    fun `distance for one degree of latitude is about 111km`() {
        val distance = GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111195.0, distance, 500.0)
    }

    @Test
    fun `bearing due north is zero`() {
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.01)
    }

    @Test
    fun `bearing due east is ninety`() {
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01)
    }

    @Test
    fun `bearing due south is 180`() {
        assertEquals(180.0, GeoMath.bearingDegrees(0.0, 0.0, -1.0, 0.0), 0.01)
    }

    @Test
    fun `angle difference wraps around 360`() {
        assertEquals(20.0, GeoMath.angleDifference(350.0, 10.0), 0.01)
    }

    @Test
    fun `angle difference between opposite bearings is 180`() {
        assertEquals(180.0, GeoMath.angleDifference(0.0, 180.0), 0.01)
    }
}
