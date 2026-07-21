package com.ah.acr.messagebox.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ah.acr.messagebox.nav.db.NavRoute
import com.ah.acr.messagebox.nav.db.NavSegment
import com.ah.acr.messagebox.nav.db.NavWeather
import com.ah.acr.messagebox.nav.db.NavWeatherDay
import com.ah.acr.messagebox.nav.db.NavDao

@Database(
    entities = [
        MsgEntity::class,
        AddressEntity::class,
        LocationEntity::class,
        MyTrackEntity::class,
        MyTrackPointEntity::class,
        SatTrackEntity::class,
        SatTrackPointEntity::class,
        TacticalRecvEntity::class,
        NavRoute::class,
        NavSegment::class,
        NavWeather::class,
        NavWeatherDay::class
    ],
    version = 9,   // v8 -> v9: NAV 테이블 추가 (nav_route/segment/weather/weather_day)
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class MsgRoomDatabase : RoomDatabase() {

    abstract fun msgDao(): MsgDao
    abstract fun addressDao(): AddressDao
    abstract fun locationDao(): LocationDao
    abstract fun myTrackDao(): MyTrackDao
    abstract fun satTrackDao(): SatTrackDao
    abstract fun tacticalRecvDao(): TacticalRecvDao
    abstract fun navDao(): NavDao

    companion object {
        @Volatile
        private var INSTANCE: MsgRoomDatabase? = null

        // ═══════════════════════════════════════════════════════
        // ⭐ v5 → v6 마이그레이션 (2026-05-03)
        // 중복 수신 패킷 차단을 위한 dedup_hash + received_at_ms 컬럼 추가
        //
        // 배경:
        // - 단말이 RECEIVED=N,OK 응답을 못 받으면 같은 메시지를 재전송
        // - RECEIVED=N의 N은 BLE 인박스 슬롯 인덱스 (재사용됨)
        // - N으로는 중복 판별 불가 → payload 내용 기반 hash + 시간 윈도우로 차단
        // ═══════════════════════════════════════════════════════
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // messages 테이블
                db.execSQL("ALTER TABLE messages ADD COLUMN dedup_hash TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN received_at_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_dedup ON messages(dedup_hash, received_at_ms)")

                // locations 테이블
                db.execSQL("ALTER TABLE locations ADD COLUMN dedup_hash TEXT")
                db.execSQL("ALTER TABLE locations ADD COLUMN received_at_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_loc_dedup ON locations(dedup_hash, received_at_ms)")
            }
        }

        // ═══════════════════════════════════════════════════════
        // ⭐ v6 → v7 마이그레이션: ack_state 컬럼 추가
        // 0=없음, 1=서버도착(V), 2=상대도착(VV). isSend(큐관리)와 분리하여
        // ACK 활성화 시에만 서버 ~A:/~D: 수신으로 V/VV 표시.
        // ═══════════════════════════════════════════════════════
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN ack_state INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7 -> v8: tactical_recv ...
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tactical_recv (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "code_num TEXT, from_imei TEXT, payload TEXT, " +
                    "is_send INTEGER NOT NULL DEFAULT 0, " +
                    "recv_at INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v8 -> v9: NAV 테이블 4종 추가 (구간 분할 + 7일 날씨 저장)
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nav_route` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mode` TEXT, " +
                    "`origin_lat` REAL NOT NULL, `origin_lon` REAL NOT NULL, " +
                    "`dest_lat` REAL NOT NULL, `dest_lon` REAL NOT NULL, `dest_name` TEXT, " +
                    "`total_distance_m` REAL NOT NULL, `total_duration_s` INTEGER NOT NULL, " +
                    "`cruise_speed` REAL, `geometry` TEXT, `source_api` TEXT, " +
                    "`fetched_at` INTEGER NOT NULL, `status` TEXT NOT NULL)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nav_segment` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`route_id` INTEGER NOT NULL, `seq` INTEGER NOT NULL, " +
                    "`rep_lat` REAL NOT NULL, `rep_lon` REAL NOT NULL, " +
                    "`dist_from_start_m` REAL NOT NULL, `eta_offset_s` INTEGER NOT NULL, `label` TEXT, " +
                    "FOREIGN KEY(`route_id`) REFERENCES `nav_route`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nav_segment_route_id` ON `nav_segment` (`route_id`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nav_weather` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`segment_id` INTEGER NOT NULL, `marine` INTEGER NOT NULL, " +
                    "`fetched_at` INTEGER NOT NULL, `json_daily` TEXT, " +
                    "FOREIGN KEY(`segment_id`) REFERENCES `nav_segment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nav_weather_segment_id` ON `nav_weather` (`segment_id`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nav_weather_day` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weather_id` INTEGER NOT NULL, " +
                    "`date` TEXT, `t_min` REAL, `t_max` REAL, `precip_mm` REAL, " +
                    "`wind_max` REAL, `wave_max` REAL, `weather_code` INTEGER, " +
                    "FOREIGN KEY(`weather_id`) REFERENCES `nav_weather`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nav_weather_day_weather_id` ON `nav_weather_day` (`weather_id`)")
            }
        }

        fun getDatabase(context: Context): MsgRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MsgRoomDatabase::class.java,
                    "msgbox.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()    // ...
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
