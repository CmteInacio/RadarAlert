package com.radaralert.app

import android.app.Application
import androidx.room.Room
import com.radaralert.app.data.radar.GitCsvFetcher
import com.radaralert.app.data.radar.RadarDatabase
import com.radaralert.app.data.radar.RadarRepository

class RadarAlertApp : Application() {

    lateinit var radarRepository: RadarRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(this, RadarDatabase::class.java, "radaralert.db").build()
        val prefs = getSharedPreferences("radaralert_prefs", MODE_PRIVATE)
        val fetcher = GitCsvFetcher(prefs)
        radarRepository = RadarRepository(database.radarDao(), fetcher)
    }
}
