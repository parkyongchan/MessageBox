package com.ah.acr.messagebox.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * A single GPS point received from the TYTO device during TRACK mode.
 */
@Entity(
        tableName = "sat_track_points",
        foreignKeys = @ForeignKey(
                entity = SatTrackEntity.class,
                parentColumns = "id",
                childColumns = "track_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("track_id")}
)
public class SatTrackPointEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "track_id")
    private int trackId;

    @ColumnInfo(name = "latitude")
    private double latitude;

    @ColumnInfo(name = "longitude")
    private double longitude;

    @ColumnInfo(name = "altitude")
    private double altitude;

    /** km/h */
    @ColumnInfo(name = "speed")
    private double speed;

    /** degrees (0-359) */
    @ColumnInfo(name = "bearing")
    private double bearing;

    /** Device-reported timestamp (UTC) - may be null for older protocol versions */
    @ColumnInfo(name = "timestamp")
    private Date timestamp;

    /** When we (the phone) actually received this packet */
    @ColumnInfo(name = "received_at")
    private Date receivedAt;

    /** Source packet type: 0x11, 0x12, 0x13 */
    @ColumnInfo(name = "track_mode")
    private int trackMode;


    public SatTrackPointEntity(
            int id,
            int trackId,
            double latitude,
            double longitude,
            double altitude,
            double speed,
            double bearing,
            Date timestamp,
            Date receivedAt,
            int trackMode
    ) {
        this.id = id;
        this.trackId = trackId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.bearing = bearing;
        this.timestamp = timestamp;
        this.receivedAt = receivedAt;
        this.trackMode = trackMode;
    }

    // Getters
    public int getId() { return id; }
    public int getTrackId() { return trackId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAltitude() { return altitude; }
    public double getSpeed() { return speed; }
    public double getBearing() { return bearing; }
    public Date getTimestamp() { return timestamp; }
    public Date getReceivedAt() { return receivedAt; }
    public int getTrackMode() { return trackMode; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setTrackId(int trackId) { this.trackId = trackId; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }
    public void setSpeed(double speed) { this.speed = speed; }
    public void setBearing(double bearing) { this.bearing = bearing; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setReceivedAt(Date receivedAt) { this.receivedAt = receivedAt; }
    public void setTrackMode(int trackMode) { this.trackMode = trackMode; }
}
