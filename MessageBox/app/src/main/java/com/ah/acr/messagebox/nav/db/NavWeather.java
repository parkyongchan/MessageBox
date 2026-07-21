package com.ah.acr.messagebox.nav.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 구간 7일 날씨(원본 보관). (기획서 6장 nav_weather)
 * 표시는 정규화 테이블(nav_weather_day)로, 원본은 재파싱/디버깅용.
 */
@Entity(
        tableName = "nav_weather",
        foreignKeys = @ForeignKey(
                entity = NavSegment.class,
                parentColumns = "id",
                childColumns = "segment_id",
                onDelete = ForeignKey.CASCADE),
        indices = { @Index("segment_id") }
)
public class NavWeather {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "segment_id") public long segmentId;

    /** 해상 예보 여부(선박) 0/1 */
    public int marine;

    /** 확보 시각(epoch ms) */
    @ColumnInfo(name = "fetched_at") public long fetchedAt;

    /** 7일 일별 예보 원본(JSON 직렬화) */
    @ColumnInfo(name = "json_daily") public String jsonDaily;
}
