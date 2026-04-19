package com.ah.acr.messagebox.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ah.acr.messagebox.MainActivity
import com.ah.acr.messagebox.R
import com.ah.acr.messagebox.database.MsgRoomDatabase
import com.ah.acr.messagebox.database.MyTrackPointEntity
import kotlinx.coroutines.*
import java.util.Date


/**
 * Location tracking foreground service
 *
 * - Uses Android LocationManager (no Google Play Services dependency)
 * - Maintains foreground notification for background tracking
 * - Saves GPS points to Room DB
 * - Broadcasts location updates to Fragment
 *
 * Java usage:
 *   LocationTrackingService.start(context, trackId, intervalSec, minDistance);
 *   LocationTrackingService.stop(context);
 *   boolean running = LocationTrackingService.isServiceRunning;
 *   int id = LocationTrackingService.currentTrackId;
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingSvc"

        // Intent Extras
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_INTERVAL_SEC = "interval_sec"
        const val EXTRA_MIN_DISTANCE = "min_distance"

        // Intent Actions
        const val ACTION_START = "com.ah.acr.messagebox.LOCATION_START"
        const val ACTION_STOP = "com.ah.acr.messagebox.LOCATION_STOP"

        // Broadcast (Fragment ← Service)
        const val BROADCAST_LOCATION_UPDATE = "com.ah.acr.messagebox.LOCATION_UPDATE"
        const val BROADCAST_SERVICE_STATE = "com.ah.acr.messagebox.SERVICE_STATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_IS_RUNNING = "is_running"

        // Notification
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Location Tracking"
        private const val NOTIFICATION_ID = 1001

        // ⭐ Service state - Java-accessible
        @JvmField
        var isServiceRunning: Boolean = false

        @JvmField
        var currentTrackId: Int = -1

        /** ⭐ Start tracking - Java-callable */
        @JvmStatic
        fun start(context: Context, trackId: Int, intervalSec: Int, minDistance: Int) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_INTERVAL_SEC, intervalSec)
                putExtra(EXTRA_MIN_DISTANCE, minDistance)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** ⭐ Stop tracking - Java-callable */
        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }


    private var locationManager: LocationManager? = null
    private var trackId: Int = -1
    private var intervalMs: Long = 30_000L
    private var minDistanceM: Float = 20f
    private var pointCount: Int = 0
    private var lastLocation: Location? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    // ═══════════════════════════════════════════════════════
    // Location Listener
    // ═══════════════════════════════════════════════════════

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            Log.v(TAG, "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabled: $provider")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Not used in modern API
        }
    }


    // ═══════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        Log.v(TAG, "Service created")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                trackId = intent.getIntExtra(EXTRA_TRACK_ID, -1)
                val intervalSec = intent.getIntExtra(EXTRA_INTERVAL_SEC, 30)
                val minDistance = intent.getIntExtra(EXTRA_MIN_DISTANCE, 20)

                intervalMs = intervalSec * 1000L
                minDistanceM = minDistance.toFloat()

                Log.v(TAG, "Starting tracking: trackId=$trackId, interval=${intervalSec}s, minDist=${minDistance}m")

                startForeground(NOTIFICATION_ID, buildNotification(0))
                startLocationUpdates()

                isServiceRunning = true
                currentTrackId = trackId
                broadcastServiceState(true)
            }

            ACTION_STOP -> {
                Log.v(TAG, "Stopping tracking")
                stopLocationUpdates()
                isServiceRunning = false
                currentTrackId = -1
                broadcastServiceState(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }


    override fun onDestroy() {
        Log.v(TAG, "Service destroyed")
        stopLocationUpdates()
        serviceScope.cancel()
        isServiceRunning = false
        currentTrackId = -1
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? = null


    // ═══════════════════════════════════════════════════════
    // Location Updates
    // ═══════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission missing!")
            stopSelf()
            return
        }

        try {
            // GPS provider (outdoor accuracy)
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    minDistanceM,
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.v(TAG, "GPS provider started")
            }

            // Network provider (indoor fallback)
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    minDistanceM,
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.v(TAG, "Network provider started")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start updates: ${e.message}")
        }
    }


    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping updates: ${e.message}")
        }
    }


    // ═══════════════════════════════════════════════════════
    // New Location Handler
    // ═══════════════════════════════════════════════════════

    private fun handleNewLocation(location: Location) {
        // Skip low-accuracy points (50m+)
        if (location.accuracy > 50f) {
            Log.v(TAG, "Low accuracy (${location.accuracy}m), skipping")
            return
        }

        Log.v(
            TAG, "New location: lat=${location.latitude}, lng=${location.longitude}, " +
                    "alt=${location.altitude}, speed=${location.speed * 3.6f} km/h, " +
                    "accuracy=${location.accuracy}m"
        )

        lastLocation = location
        pointCount++

        // Save to DB
        saveToDatabase(location)

        // Broadcast to Fragment
        broadcastLocationUpdate(location)

        // Update notification
        updateNotification(pointCount)
    }


    private fun saveToDatabase(location: Location) {
        if (trackId < 0) return

        serviceScope.launch {
            try {
                val point = MyTrackPointEntity(
                    0,
                    trackId,
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.speed * 3.6,
                    location.bearing.toDouble(),
                    location.accuracy.toDouble(),
                    Date(location.time)
                )

                val dao = MsgRoomDatabase.getDatabase(applicationContext).myTrackDao()
                dao.insertPoint(point)

                val count = dao.getPointCount(trackId)

                // Recalculate stats
                recalculateAndUpdateStats(trackId, dao)

                Log.v(TAG, "Saved point #$count to track $trackId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save point: ${e.message}")
            }
        }
    }


    private suspend fun recalculateAndUpdateStats(
        trackId: Int,
        dao: com.ah.acr.messagebox.database.MyTrackDao
    ) {
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


    // ═══════════════════════════════════════════════════════
    // Broadcast
    // ═══════════════════════════════════════════════════════

    private fun broadcastLocationUpdate(location: Location) {
        val intent = Intent(BROADCAST_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ALTITUDE, location.altitude)
            putExtra(EXTRA_SPEED, location.speed * 3.6)
            putExtra(EXTRA_BEARING, location.bearing.toDouble())
            putExtra(EXTRA_ACCURACY, location.accuracy.toDouble())
            putExtra(EXTRA_TIMESTAMP, location.time)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun broadcastServiceState(isRunning: Boolean) {
        val intent = Intent(BROADCAST_SERVICE_STATE).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_TRACK_ID, trackId)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    // ═══════════════════════════════════════════════════════
    // Notification (required for foreground service)
    // ═══════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Location tracking service notification"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }


    private fun buildNotification(pointCount: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (pointCount == 0) {
            "Waiting for GPS..."
        } else {
            "$pointCount points recorded"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🔴 Location Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }


    private fun updateNotification(pointCount: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(pointCount))
    }


    // ═══════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}