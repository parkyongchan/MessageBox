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
 * ViewModel for My Tracks (phone GPS tracking sessions)
 */
class MyTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: MyTrackDao = MsgRoomDatabase.getDatabase(application).myTrackDao()
    private val repository = MyTrackRepository(dao)

    // LiveData
    val allTracks: LiveData<List<MyTrackEntity>> = repository.allTracks
    val completedTracks: LiveData<List<MyTrackEntity>> = repository.completedTracks

    fun getActiveTrack(): LiveData<MyTrackEntity?> = dao.getActiveTrack()

    fun getTrackById(id: Int): LiveData<MyTrackEntity?> = dao.getTrackById(id)

    fun getPointsByTrack(trackId: Int): LiveData<List<MyTrackPointEntity>> =
        dao.getPointsByTrack(trackId)


    // ═══════════════════════════════════════════════════════
    // Track Session Management
    // ═══════════════════════════════════════════════════════

    /**
     * Create a new tracking session.
     * Callback receives the new track ID.
     *
     * Uses Function<Long, Unit> for Java compatibility.
     */
    fun startNewTrack(
        name: String,
        intervalSec: Int,
        minDistance: Int,
        onComplete: Function<Long, Unit>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = MyTrackEntity(
                0,
                name,
                Date(),
                null,
                0.0,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                "ACTIVE",
                intervalSec,
                minDistance,
                Date()
            )
            val id = dao.insertTrack(track)
            withContext(Dispatchers.Main) {
                onComplete.apply(id)
            }
        }
    }


    /** Pause tracking */
    fun pauseTrack(trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateTrackStatus(trackId, "PAUSED")
        }
    }


    /** Resume tracking */
    fun resumeTrack(trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateTrackStatus(trackId, "ACTIVE")
        }
    }


    /** Stop and mark as completed */
    fun stopTrack(trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.completeTrack(trackId, Date())
        }
    }


    /** Delete a track */
    fun deleteTrack(track: MyTrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteTrack(track)
        }
    }


    /** Rename a track - uses DAO query directly (no copy() needed) */
    fun renameTrack(trackId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.renameTrack(trackId, newName)
        }
    }


    // ═══════════════════════════════════════════════════════
    // Point Management
    // ═══════════════════════════════════════════════════════

    fun addPoint(point: MyTrackPointEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPoint(point)
        }
    }
}