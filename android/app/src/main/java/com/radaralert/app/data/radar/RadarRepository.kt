package com.radaralert.app.data.radar

import com.radaralert.app.domain.RadarPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RadarRepository(
    private val dao: RadarDao,
    private val fetcher: GitCsvFetcher
) {
    suspend fun syncIfNeeded() = withContext(Dispatchers.IO) {
        if (!fetcher.remoteVersionChanged()) return@withContext
        val csv = fetcher.fetchCsvAndMarkVersion() ?: return@withContext
        dao.replaceAll(RadarCsvParser.parse(csv))
    }

    suspend fun getAllRadars(): List<RadarPoint> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }
    }
}
