package com.ah.acr.messagebox.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * 내 위치 추적 세션 (Track Session)
 * 
 * 한 번의 추적 시작 ~ 종료가 하나의 세션
 * 예: "2026-04-18 08:00 ~ 10:00 등산"
 */
@Entity(tableName = "my_tracks")
public class MyTrackEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    /** 사용자 지정 이름 (예: "아침 등산", 기본값은 시작 시간) */
    @ColumnInfo(name = "name")
    private String name;

    /** 시작 시간 */
    @ColumnInfo(name = "start_time")
    private Date startTime;

    /** 종료 시간 (NULL이면 진행 중) */
    @ColumnInfo(name = "end_time")
    private Date endTime;

    /** 총 거리 (미터) */
    @ColumnInfo(name = "total_distance")
    private double totalDistance;

    /** 포인트 개수 */
    @ColumnInfo(name = "point_count")
    private int pointCount;

    /** 평균 속도 (km/h) */
    @ColumnInfo(name = "avg_speed")
    private double avgSpeed;

    /** 최고 속도 (km/h) */
    @ColumnInfo(name = "max_speed")
    private double maxSpeed;

    /** 최저 고도 (m) */
    @ColumnInfo(name = "min_altitude")
    private double minAltitude;

    /** 최고 고도 (m) */
    @ColumnInfo(name = "max_altitude")
    private double maxAltitude;

    /** 상태: 'ACTIVE' (추적 중) | 'COMPLETED' (완료) | 'PAUSED' (일시정지) */
    @ColumnInfo(name = "status")
    private String status;

    /** 기록 주기 - 시간 (초) */
    @ColumnInfo(name = "interval_sec")
    private int intervalSec;

    /** 기록 주기 - 최소 거리 (m) */
    @ColumnInfo(name = "min_distance")
    private int minDistance;

    /** 생성 시간 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;


    public MyTrackEntity() {}

    public MyTrackEntity(int id, String name, Date startTime, Date endTime,
                         double totalDistance, int pointCount,
                         double avgSpeed, double maxSpeed,
                         double minAltitude, double maxAltitude,
                         String status, int intervalSec, int minDistance,
                         Date createdAt) {
        this.id = id;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalDistance = totalDistance;
        this.pointCount = pointCount;
        this.avgSpeed = avgSpeed;
        this.maxSpeed = maxSpeed;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        this.status = status;
        this.intervalSec = intervalSec;
        this.minDistance = minDistance;
        this.createdAt = createdAt;
    }


    // ═══ Getters/Setters ═══

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public int getPointCount() { return pointCount; }
    public void setPointCount(int pointCount) { this.pointCount = pointCount; }

    public double getAvgSpeed() { return avgSpeed; }
    public void setAvgSpeed(double avgSpeed) { this.avgSpeed = avgSpeed; }

    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }

    public double getMinAltitude() { return minAltitude; }
    public void setMinAltitude(double minAltitude) { this.minAltitude = minAltitude; }

    public double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(double maxAltitude) { this.maxAltitude = maxAltitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getIntervalSec() { return intervalSec; }
    public void setIntervalSec(int intervalSec) { this.intervalSec = intervalSec; }

    public int getMinDistance() { return minDistance; }
    public void setMinDistance(int minDistance) { this.minDistance = minDistance; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }


    // ═══ Helper ═══

    /** 소요 시간 (밀리초) */
    public long getDurationMillis() {
        if (startTime == null) return 0;
        Date end = (endTime != null) ? endTime : new Date();
        return end.getTime() - startTime.getTime();
    }

    /** 진행 중? */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isPaused() {
        return "PAUSED".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}
