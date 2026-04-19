package com.ah.acr.messagebox.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.function.Function


/**
 * ViewModel for Satellite TRACK sessions.
 */
class SatTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: SatTrackDao = MsgRoomDatabase.getDatabase(application).satTrackDao()
    private val repository = SatTrackRepository(dao)

    val allTracks: LiveData<List<SatTrackEntity>> = repository.allTracks
    val completedTracks: LiveData<List<SatTrackEntity>> = repository.completedTracks

    fun getActiveTrack(): LiveData<SatTrackEntity?> = dao.getActiveTrack()
    fun getTrackById(id: Int): LiveData<SatTrackEntity?> = dao.getTrackById(id)
    fun getPointsByTrack(trackId: Int): LiveData<List<SatTrackPointEntity>> =
        dao.getPointsByTrack(trackId)


    /** Create a new satellite track session (Java-compatible callback) */
    fun startNewTrack(
        name: String,
        imei: String?,
        onComplete: Function<Long, Unit>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = SatTrackEntity(
                0,
                name,
                imei,
                Date(),
                null,
                0.0,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                "ACTIVE",
                Date()
            )
            val id = dao.insertTrack(track)
            withContext(Dispatchers.Main) {
                onComplete.apply(id)
            }
        }
    }


    /** Stop and mark as completed */
    fun stopTrack(trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.completeTrack(trackId, Date())
        }
    }


    fun deleteTrack(track: SatTrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteTrack(track)
        }
    }


    fun renameTrack(trackId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.renameTrack(trackId, newName)
        }
    }


    fun addPoint(point: SatTrackPointEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPoint(point)
        }
    }
}
