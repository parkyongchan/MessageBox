package com.ah.acr.messagebox.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


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
    version = 5,   // ⭐ v4 → v5 (sort by create_at instead of id, self-echo matching)
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

        fun getDatabase(context: Context): MsgRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MsgRoomDatabase::class.java,
                    "msgbox.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
