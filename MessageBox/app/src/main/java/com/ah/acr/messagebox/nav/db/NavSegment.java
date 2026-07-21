package com.ah.acr.messagebox.nav.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * NAV 경로 구간. (기획서 6장 nav_segment)
 * 각 구간 대표 좌표(rep_lat/rep_lon)에서 7일 날씨를 확보한다.
 */
@Entity(
        tableName = "nav_segment",
        foreignKeys = @ForeignKey(
                entity = NavRoute.class,
                parentColumns = "id",
                childColumns = "route_id",
                onDelete = ForeignKey.CASCADE),
        indices = { @Index("route_id") }
)
public class NavSegment {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "route_id") public long routeId;

    /** 구간 순번(0..N) */
    public int seq;

    @ColumnInfo(name = "rep_lat") public double repLat;
    @ColumnInfo(name = "rep_lon") public double repLon;

    /** 출발 기준 누적거리(m) */
    @ColumnInfo(name = "dist_from_start_m") public double distFromStartM;

    /** 출발 기준 도달 예상 오프셋(초) */
    @ColumnInfo(name = "eta_offset_s") public long etaOffsetS;

    /** 구간 표시명(선택, 예: '50km 지점') */
    public String label;
}
