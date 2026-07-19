package com.radaralert.app.data.radar

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RadarEntity::class], version = 1, exportSchema = false)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun radarDao(): RadarDao
}
