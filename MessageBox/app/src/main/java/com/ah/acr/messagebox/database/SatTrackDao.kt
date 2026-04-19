package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date


@Dao
interface SatTrackDao {

    // ═══════════════════════════════════════════════════════
    // Track Session
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: SatTrackEntity): Long

    @Update
    suspend fun updateTrack(track: SatTrackEntity)

    @Delete
    suspend fun deleteTrack(track: SatTrackEntity)

    @Query("SELECT * FROM sat_tracks ORDER BY start_time DESC")
    fun getAllTracks(): LiveData<List<SatTrackEntity>>

    @Query("SELECT * FROM sat_tracks WHERE id = :id")
    fun getTrackById(id: Int): LiveData<SatTrackEntity?>

    @Query("SELECT * FROM sat_tracks WHERE id = :id")
    suspend fun getTrackByIdSync(id: Int): SatTrackEntity?

    @Query("SELECT * FROM sat_tracks WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveTrack(): LiveData<SatTrackEntity?>

    @Query("SELECT * FROM sat_tracks WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTrackSync(): SatTrackEntity?

    @Query("SELECT * FROM sat_tracks WHERE status = 'COMPLETED' ORDER BY start_time DESC")
    fun getCompletedTracks(): LiveData<List<SatTrackEntity>>

    // Status updates
    @Query("UPDATE sat_tracks SET status = :status WHERE id = :trackId")
    suspend fun updateTrackStatus(trackId: Int, status: String)

    @Query("UPDATE sat_tracks SET status = 'COMPLETED', end_time = :endTime WHERE id = :trackId")
    suspend fun completeTrack(trackId: Int, endTime: Date)

    // Rename
    @Query("UPDATE sat_tracks SET name = :newName WHERE id = :trackId")
    suspend fun renameTrack(trackId: Int, newName: String)

    // Stats update
    @Query("""
        UPDATE sat_tracks
        SET total_distance = :distance,
            point_count = :count,
            avg_speed = :avgSpeed,
            max_speed = :maxSpeed,
            min_altitude = :minAlt,
            max_altitude = :maxAlt
        WHERE id = :trackId
    """)
    suspend fun updateTrackStats(
        trackId: Int,
        distance: Double,
        count: Int,
        avgSpeed: Double,
        maxSpeed: Double,
        minAlt: Double,
        maxAlt: Double
    )


    // ═══════════════════════════════════════════════════════
    // Track Point
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SatTrackPointEntity): Long

    @Query("SELECT * FROM sat_track_points WHERE track_id = :trackId ORDER BY received_at ASC")
    fun getPointsByTrack(trackId: Int): LiveData<List<SatTrackPointEntity>>

    @Query("SELECT * FROM sat_track_points WHERE track_id = :trackId ORDER BY received_at ASC")
    suspend fun getPointsByTrackSync(trackId: Int): List<SatTrackPointEntity>

    @Query("SELECT * FROM sat_track_points WHERE track_id = :trackId ORDER BY received_at DESC LIMIT 1")
    suspend fun getLatestPoint(trackId: Int): SatTrackPointEntity?

    @Query("SELECT COUNT(*) FROM sat_track_points WHERE track_id = :trackId")
    suspend fun getPointCount(trackId: Int): Int

    @Query("DELETE FROM sat_track_points WHERE track_id = :trackId")
    suspend fun deletePointsByTrack(trackId: Int)
}
