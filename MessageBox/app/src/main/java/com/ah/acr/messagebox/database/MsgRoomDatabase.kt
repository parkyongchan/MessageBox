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
        SatTrackPointEntity::class
    ],
    version = 6,   // ⭐ v5 → v6: dedup_hash + received_at_ms columns (중복 수신 차단)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MsgRoomDatabase : RoomDatabase() {

    abstract fun msgDao(): MsgDao
    abstract fun addressDao(): AddressDao
    abstract fun locationDao(): LocationDao
    abstract fun myTrackDao(): MyTrackDao
    abstract fun satTrackDao(): SatTrackDao

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

        fun getDatabase(context: Context): MsgRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MsgRoomDatabase::class.java,
                    "msgbox.db"
                )
                    .addMigrations(MIGRATION_5_6)        // ⭐ 정식 마이그레이션 등록
                    .fallbackToDestructiveMigration()    // 보험용 (마이그레이션 실패 시에만 작동)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
