package com.ah.acr.messagebox.nav.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * NAV 경로 헤더. (기획서 6장 nav_route)
 */
@Entity(tableName = "nav_route")
public class NavRoute {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** WALK / VEHICLE / VESSEL */
    public String mode;

    @ColumnInfo(name = "origin_lat") public double originLat;
    @ColumnInfo(name = "origin_lon") public double originLon;
    @ColumnInfo(name = "dest_lat")   public double destLat;
    @ColumnInfo(name = "dest_lon")   public double destLon;

    /** 목적지 표시명(선택) */
    @ColumnInfo(name = "dest_name")  public String destName;

    @ColumnInfo(name = "total_distance_m") public double totalDistanceM;
    @ColumnInfo(name = "total_duration_s") public long   totalDurationS;

    /** 선박 순항속력 등 계산 입력값(선택, 노트) */
    @ColumnInfo(name = "cruise_speed") public Double cruiseSpeed;

    /** 경로 지오메트리(encoded polyline 또는 좌표열) */
    public String geometry;

    /** osrm / ors 등 출처 */
    @ColumnInfo(name = "source_api") public String sourceApi;

    /** 확보 시각(epoch ms) */
    @ColumnInfo(name = "fetched_at") public long fetchedAt;

    /** PLANNED / ACTIVE / DONE */
    @NonNull
    public String status = "PLANNED";
}
