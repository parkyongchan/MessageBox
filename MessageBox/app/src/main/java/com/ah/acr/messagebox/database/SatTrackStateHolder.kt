package com.ah.acr.messagebox.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date


/**
 * Shared state between MainActivity and DevicesTabFragment.
 *
 * MainActivity.receivePacketProcess() checks this holder whenever
 * a location packet arrives. If a satellite TRACK session is active,
 * the point is also saved to sat_track_points table.
 *
 * This is needed because:
 *   - MainActivity receives BLE packets (centralized)
 *   - DevicesTabFragment starts/stops the session
 *   - Both need to agree on "is a session currently active?"
 */
object SatTrackStateHolder {

    private const val TAG = "SatTrackStateHolder"

    @JvmField
    @Volatile
    var activeTrackId: Int = -1

    @JvmField
    @Volatile
    var activeTrackImei: String? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    /** Called by Fragment when [Start TRACK] is pressed */
    @JvmStatic
    fun startSession(trackId: Int, imei: String?) {
        activeTrackId = trackId
        activeTrackImei = imei
        Log.v(TAG, "Session started: trackId=$trackId, imei=$imei")
    }


    /** Called by Fragment when [Stop TRACK] is pressed */
    @JvmStatic
    fun stopSession() {
        Log.v(TAG, "Session stopped (was trackId=$activeTrackId)")
        activeTrackId = -1
        activeTrackImei = null
    }


    @JvmStatic
    fun isSessionActive(): Boolean = activeTrackId > 0


    /**
     * Called by MainActivity when a location packet arrives.
     * If a satellite session is active, saves the point to sat_track_points.
     *
     * Java usage:
     *   SatTrackStateHolder.recordPoint(context, lat, lng, alt, speed, bearing,
     *                                    deviceTimestamp, trackMode);
     */
    @JvmStatic
    fun recordPoint(
        context: Context,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speed: Double,
        bearing: Double,
        deviceTimestamp: Date?,
        trackMode: Int
    ) {
        val trackId = activeTrackId
        if (trackId <= 0) return  // No active session

        scope.launch {
            try {
                val dao = MsgRoomDatabase.getDatabase(context).satTrackDao()

                val point = SatTrackPointEntity(
                    0,
                    trackId,
                    latitude,
                    longitude,
                    altitude,
                    speed,
                    bearing,
                    deviceTimestamp,
                    Date(),
                    trackMode
                )

                dao.insertPoint(point)

                // Recalculate track stats
                recalculateStats(dao, trackId)

                Log.v(TAG, "Point recorded for track $trackId: $latitude, $longitude")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record point: ${e.message}")
            }
        }
    }


    private suspend fun recalculateStats(dao: SatTrackDao, trackId: Int) {
        val points = dao.getPointsByTrackSync(trackId)
        if (points.isEmpty()) return

        var totalDistance = 0.0
        var maxSpeed = 0.0
        var sumSpeed = 0.0
        var minAlt = Double.MAX_VALUE
        var maxAlt = Double.MIN_VALUE

        for (i in points.indices) {
            val p = points[i]
            if (i > 0) {
                val prev = points[i - 1]
                totalDistance += haversine(
                    prev.latitude, prev.longitude,
                    p.latitude, p.longitude
                )
            }
            if (p.speed > maxSpeed) maxSpeed = p.speed
            sumSpeed += p.speed
            if (p.altitude < minAlt) minAlt = p.altitude
            if (p.altitude > maxAlt) maxAlt = p.altitude
        }

        dao.updateTrackStats(
            trackId,
            totalDistance,
            points.size,
            sumSpeed / points.size,
            maxSpeed,
            if (minAlt == Double.MAX_VALUE) 0.0 else minAlt,
            if (maxAlt == Double.MIN_VALUE) 0.0 else maxAlt
        )
    }


    private fun haversine(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
