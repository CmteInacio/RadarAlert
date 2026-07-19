package com.radaralert.app.data.radar

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RadarDao {

    @Query("SELECT * FROM radares")
    suspend fun getAll(): List<RadarEntity>

    @Query("DELETE FROM radares")
    suspend fun clear()

    @Insert
    suspend fun insertAll(radares: List<RadarEntity>)

    @Transaction
    suspend fun replaceAll(radares: List<RadarEntity>) {
        clear()
        insertAll(radares)
    }
}
