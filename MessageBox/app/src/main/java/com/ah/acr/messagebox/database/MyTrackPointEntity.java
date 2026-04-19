package com.ah.acr.messagebox.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * 내 위치 추적 포인트 (GPS 샘플 하나)
 * 
 * MyTrackEntity (세션) 에 여러 개가 속함
 * 예: 1분마다 1개씩 기록
 */
@Entity(
    tableName = "my_track_points",
    foreignKeys = @ForeignKey(
        entity = MyTrackEntity.class,
        parentColumns = "id",
        childColumns = "track_id",
        onDelete = ForeignKey.CASCADE  // 세션 삭제 시 포인트도 삭제
    ),
    indices = {
        @Index(value = "track_id"),
        @Index(value = "timestamp")
    }
)
public class MyTrackPointEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** 소속 트랙 세션 ID */
    @ColumnInfo(name = "track_id")
    private int trackId;

    /** 위도 */
    @ColumnInfo(name = "latitude")
    private double latitude;

    /** 경도 */
    @ColumnInfo(name = "longitude")
    private double longitude;

    /** 고도 (m) */
    @ColumnInfo(name = "altitude")
    private double altitude;

    /** 속도 (km/h) */
    @ColumnInfo(name = "speed")
    private double speed;

    /** 방향 (0~360 degree) */
    @ColumnInfo(name = "bearing")
    private double bearing;

    /** GPS 정확도 (m) */
    @ColumnInfo(name = "accuracy")
    private double accuracy;

    /** 기록 시각 */
    @ColumnInfo(name = "timestamp")
    private Date timestamp;


    public MyTrackPointEntity() {}

    public MyTrackPointEntity(int id, int trackId,
                              double latitude, double longitude,
                              double altitude, double speed, double bearing,
                              double accuracy, Date timestamp) {
        this.id = id;
        this.trackId = trackId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
    }


    // ═══ Getters/Setters ═══

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getBearing() { return bearing; }
    public void setBearing(double bearing) { this.bearing = bearing; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
