package com.radaralert.app.domain

class GpsIntervalStrategy(
    private val lowSpeedThresholdKmh: Double = 30.0,
    private val fastIntervalMillis: Long = 1000L,
    private val slowIntervalMillis: Long = 4000L
) {
    fun intervalFor(speedKmh: Double): Long =
        if (speedKmh < lowSpeedThresholdKmh) slowIntervalMillis else fastIntervalMillis
}
