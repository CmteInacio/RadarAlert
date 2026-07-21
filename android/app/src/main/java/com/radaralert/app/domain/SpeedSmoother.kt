package com.radaralert.app.domain

class SpeedSmoother(private val windowSize: Int = 2) {
    private val readings = ArrayDeque<Double>()

    fun addAndSmooth(speedKmh: Double): Double {
        readings.addLast(speedKmh)
        if (readings.size > windowSize) readings.removeFirst()
        return readings.average()
    }
}
