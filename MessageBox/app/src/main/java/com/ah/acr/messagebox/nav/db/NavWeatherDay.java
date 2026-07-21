package com.ah.acr.messagebox.nav.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 일별 파싱 결과(정규화). (기획서 6장 nav_weather_day)
 * 표시 속도용. nav_weather.json_daily 를 파싱해 채운다.
 */
@Entity(
        tableName = "nav_weather_day",
        foreignKeys = @ForeignKey(
                entity = NavWeather.class,
                parentColumns = "id",
                childColumns = "weather_id",
                onDelete = ForeignKey.CASCADE),
        indices = { @Index("weather_id") }
)
public class NavWeatherDay {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "weather_id") public long weatherId;

    /** YYYY-MM-DD */
    public String date;

    @ColumnInfo(name = "t_min") public Double tMin;
    @ColumnInfo(name = "t_max") public Double tMax;

    @ColumnInfo(name = "precip_mm") public Double precipMm;

    @ColumnInfo(name = "wind_max") public Double windMax;

    /** 최대 파고(해상, 선박) */
    @ColumnInfo(name = "wave_max") public Double waveMax;

    /** WMO 날씨코드(대표 날씨 표시용) */
    @ColumnInfo(name = "weather_code") public Integer weatherCode;
}
