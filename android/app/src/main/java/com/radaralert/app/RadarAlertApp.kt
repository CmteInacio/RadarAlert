package com.radaralert.app

import android.app.Application
import androidx.room.Room
import com.radaralert.app.data.radar.GitCsvFetcher
import com.radaralert.app.data.radar.RadarDatabase
import com.radaralert.app.data.radar.RadarRepository
import com.radaralert.app.data.settings.AlertaTypePrefs

class RadarAlertApp : Application() {

    lateinit var radarRepository: RadarRepository
        private set

    lateinit var alertaTypePrefs: AlertaTypePrefs
        private set

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(this, RadarDatabase::class.java, "radaralert.db")
            // Projeto em desenvolvimento ativo: aceitar perder o cache local de
            // radares ao mudar o schema é mais simples que escrever migrations
            // a cada ajuste. Basta ressincronizar com o repositório.
            .fallbackToDestructiveMigration()
            .build()
        val prefs = getSharedPreferences("radaralert_prefs", MODE_PRIVATE)
        val fetcher = GitCsvFetcher(prefs)
        radarRepository = RadarRepository(database.radarDao(), fetcher)
        alertaTypePrefs = AlertaTypePrefs(prefs)
    }
}
