package com.ah.acr.messagebox.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Satellite TRACK session - records TRACK mode activity from a connected TYTO device.
 *
 * Flow:
 *   1. User taps [Start TRACK] → BLE command "LOCATION=2"
 *   2. Session row inserted (status = ACTIVE)
 *   3. Incoming 0x11/0x12/0x13 packets → points saved to sat_track_points
 *   4. User taps [Stop TRACK] → BLE command "LOCATION=3"
 *   5. Session marked COMPLETED
 */
@Entity(tableName = "sat_tracks")
public class SatTrackEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    /** IMEI of the TYTO device used */
    @ColumnInfo(name = "imei")
    private String imei;

    @ColumnInfo(name = "start_time")
    private Date startTime;

    @ColumnInfo(name = "end_time")
    private Date endTime;

    @ColumnInfo(name = "total_distance")
    private double totalDistance;

    @ColumnInfo(name = "point_count")
    private int pointCount;

    @ColumnInfo(name = "avg_speed")
    private double avgSpeed;

    @ColumnInfo(name = "max_speed")
    private double maxSpeed;

    @ColumnInfo(name = "min_altitude")
    private double minAltitude;

    @ColumnInfo(name = "max_altitude")
    private double maxAltitude;

    /** ACTIVE, COMPLETED */
    @NonNull
    @ColumnInfo(name = "status")
    private String status;

    @ColumnInfo(name = "created_at")
    private Date createdAt;


    public SatTrackEntity(
            int id,
            @NonNull String name,
            String imei,
            Date startTime,
            Date endTime,
            double totalDistance,
            int pointCount,
            double avgSpeed,
            double maxSpeed,
            double minAltitude,
            double maxAltitude,
            @NonNull String status,
            Date createdAt
    ) {
        this.id = id;
        this.name = name;
        this.imei = imei;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalDistance = totalDistance;
        this.pointCount = pointCount;
        this.avgSpeed = avgSpeed;
        this.maxSpeed = maxSpeed;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters
    public int getId() { return id; }
    @NonNull public String getName() { return name; }
    public String getImei() { return imei; }
    public Date getStartTime() { return startTime; }
    public Date getEndTime() { return endTime; }
    public double getTotalDistance() { return totalDistance; }
    public int getPointCount() { return pointCount; }
    public double getAvgSpeed() { return avgSpeed; }
    public double getMaxSpeed() { return maxSpeed; }
    public double getMinAltitude() { return minAltitude; }
    public double getMaxAltitude() { return maxAltitude; }
    @NonNull public String getStatus() { return status; }
    public Date getCreatedAt() { return createdAt; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setName(@NonNull String name) { this.name = name; }
    public void setImei(String imei) { this.imei = imei; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
    public void setPointCount(int pointCount) { this.pointCount = pointCount; }
    public void setAvgSpeed(double avgSpeed) { this.avgSpeed = avgSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public void setMinAltitude(double minAltitude) { this.minAltitude = minAltitude; }
    public void setMaxAltitude(double maxAltitude) { this.maxAltitude = maxAltitude; }
    public void setStatus(@NonNull String status) { this.status = status; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    /** Duration in milliseconds */
    public long getDurationMillis() {
        if (startTime == null) return 0;
        Date end = endTime != null ? endTime : new Date();
        return end.getTime() - startTime.getTime();
    }
}
