package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date


@Dao
interface MyTrackDao {

    // ═══════════════════════════════════════════════════════
    // Track Session
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: MyTrackEntity): Long

    @Update
    suspend fun updateTrack(track: MyTrackEntity)

    @Delete
    suspend fun deleteTrack(track: MyTrackEntity)

    @Query("SELECT * FROM my_tracks ORDER BY start_time DESC")
    fun getAllTracks(): LiveData<List<MyTrackEntity>>

    @Query("SELECT * FROM my_tracks WHERE id = :id")
    fun getTrackById(id: Int): LiveData<MyTrackEntity?>

    @Query("SELECT * FROM my_tracks WHERE id = :id")
    suspend fun getTrackByIdSync(id: Int): MyTrackEntity?

    @Query("SELECT * FROM my_tracks WHERE status = 'ACTIVE' OR status = 'PAUSED' LIMIT 1")
    fun getActiveTrack(): LiveData<MyTrackEntity?>

    @Query("SELECT * FROM my_tracks WHERE status = 'ACTIVE' OR status = 'PAUSED' LIMIT 1")
    suspend fun getActiveTrackSync(): MyTrackEntity?

    @Query("SELECT * FROM my_tracks WHERE status = 'COMPLETED' ORDER BY start_time DESC")
    fun getCompletedTracks(): LiveData<List<MyTrackEntity>>

    @Query("SELECT * FROM my_tracks WHERE start_time >= :from AND start_time <= :to ORDER BY start_time DESC")
    fun getTracksInRange(from: Date, to: Date): LiveData<List<MyTrackEntity>>


    // Status updates
    @Query("UPDATE my_tracks SET status = :status WHERE id = :trackId")
    suspend fun updateTrackStatus(trackId: Int, status: String)

    @Query("UPDATE my_tracks SET status = 'COMPLETED', end_time = :endTime WHERE id = :trackId")
    suspend fun completeTrack(trackId: Int, endTime: Date)

    // ⭐ Rename track (directly via SQL - no need for copy())
    @Query("UPDATE my_tracks SET name = :newName WHERE id = :trackId")
    suspend fun renameTrack(trackId: Int, newName: String)

    // Stats update
    @Query("""
        UPDATE my_tracks
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
    suspend fun insertPoint(point: MyTrackPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<MyTrackPointEntity>)

    @Query("SELECT * FROM my_track_points WHERE track_id = :trackId ORDER BY timestamp ASC")
    fun getPointsByTrack(trackId: Int): LiveData<List<MyTrackPointEntity>>

    @Query("SELECT * FROM my_track_points WHERE track_id = :trackId ORDER BY timestamp ASC")
    suspend fun getPointsByTrackSync(trackId: Int): List<MyTrackPointEntity>

    @Query("SELECT * FROM my_track_points WHERE track_id = :trackId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPoint(trackId: Int): MyTrackPointEntity?

    @Query("SELECT COUNT(*) FROM my_track_points WHERE track_id = :trackId")
    suspend fun getPointCount(trackId: Int): Int

    @Query("DELETE FROM my_track_points WHERE track_id = :trackId")
    suspend fun deletePointsByTrack(trackId: Int)
}