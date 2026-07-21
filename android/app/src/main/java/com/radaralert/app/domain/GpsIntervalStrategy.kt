package com.radaralert.app.domain

class GpsIntervalStrategy(
    private val lowSpeedThresholdKmh: Double = 7.0,
    private val fastIntervalMillis: Long = 1000L,
    private val slowIntervalMillis: Long = 2000L
) {
    fun intervalFor(speedKmh: Double): Long =
        if (speedKmh < lowSpeedThresholdKmh) slowIntervalMillis else fastIntervalMillis
}
