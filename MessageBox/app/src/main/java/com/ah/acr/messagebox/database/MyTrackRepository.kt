package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import java.util.Date


class MyTrackRepository(private val dao: MyTrackDao) {

    val allTracks: LiveData<List<MyTrackEntity>> = dao.getAllTracks()
    val completedTracks: LiveData<List<MyTrackEntity>> = dao.getCompletedTracks()
    val activeTrack: LiveData<MyTrackEntity?> = dao.getActiveTrack()


    fun getTrackById(id: Int): LiveData<MyTrackEntity?> = dao.getTrackById(id)

    fun getPointsByTrack(trackId: Int): LiveData<List<MyTrackPointEntity>> =
        dao.getPointsByTrack(trackId)

    fun getTracksInRange(from: Date, to: Date): LiveData<List<MyTrackEntity>> =
        dao.getTracksInRange(from, to)


    suspend fun insertTrack(track: MyTrackEntity): Long = dao.insertTrack(track)

    suspend fun updateTrack(track: MyTrackEntity) = dao.updateTrack(track)

    suspend fun deleteTrack(track: MyTrackEntity) = dao.deleteTrack(track)

    suspend fun insertPoint(point: MyTrackPointEntity) = dao.insertPoint(point)

    suspend fun getPointsByTrackSync(trackId: Int) = dao.getPointsByTrackSync(trackId)

    suspend fun updateTrackStats(
        trackId: Int,
        distance: Double,
        count: Int,
        avgSpeed: Double,
        maxSpeed: Double,
        minAlt: Double,
        maxAlt: Double
    ) {
        dao.updateTrackStats(trackId, distance, count, avgSpeed, maxSpeed, minAlt, maxAlt)
    }


    suspend fun completeTrack(trackId: Int) {
        dao.completeTrack(trackId, Date())
    }
}
