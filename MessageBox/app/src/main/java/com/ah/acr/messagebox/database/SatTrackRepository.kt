package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData


class SatTrackRepository(private val dao: SatTrackDao) {

    val allTracks: LiveData<List<SatTrackEntity>> = dao.getAllTracks()
    val completedTracks: LiveData<List<SatTrackEntity>> = dao.getCompletedTracks()

    fun getActiveTrack(): LiveData<SatTrackEntity?> = dao.getActiveTrack()
    fun getTrackById(id: Int): LiveData<SatTrackEntity?> = dao.getTrackById(id)
    fun getPointsByTrack(trackId: Int): LiveData<List<SatTrackPointEntity>> =
        dao.getPointsByTrack(trackId)

    suspend fun insertTrack(track: SatTrackEntity): Long = dao.insertTrack(track)
    suspend fun updateTrack(track: SatTrackEntity) = dao.updateTrack(track)
    suspend fun deleteTrack(track: SatTrackEntity) = dao.deleteTrack(track)

    suspend fun insertPoint(point: SatTrackPointEntity): Long = dao.insertPoint(point)
}
