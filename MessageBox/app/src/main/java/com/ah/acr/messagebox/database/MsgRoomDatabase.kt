package com.ah.acr.messagebox.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MsgEntity::class,
        AddressEntity::class,
        LocationEntity::class,
        MyTrackEntity::class,
        MyTrackPointEntity::class,
        SatTrackEntity::class,
        SatTrackPointEntity::class,
        TacticalRecvEntity::class
    ],
    version = 8,   // v7 -> v8: tactical_recv 테이블 추가 (전술 데이터 영속화)   // v6 -> v7: ack_state column (ACK V/VV state, isSend와 분리)
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

        // v7 -> v8: tactical_recv 테이블 (전술 데이터 영속화)
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

        fun getDatabase(context: Context): MsgRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MsgRoomDatabase::class.java,
                    "msgbox.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)        // ⭐ 정식 마이그레이션 등록
                    .fallbackToDestructiveMigration()    // 보험용 (마이그레이션 실패 시에만 작동)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
