@file:JvmName("MsgRoomDatabase")

package com.ah.acr.messagebox.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.util.Date

@Database(entities = [MsgEntity::class, LocationEntity::class, AddressEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MsgRoomDatabase : RoomDatabase() {

    abstract fun msgDao(): MsgDao
    abstract fun locationDao(): LocationDao
    abstract fun addressDao(): AddressDao

    companion object {
        @Volatile
        private var INSTANCE: MsgRoomDatabase? = null

        fun getDatabase(context: Context): MsgRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    MsgRoomDatabase::class.java,
                    "enc_msg_database"
                )
                    .fallbackToDestructiveMigration()
                val factory = SupportFactory(SQLiteDatabase.getBytes("&019ahan@1".toCharArray()))
                builder.openHelperFactory(factory)
                val instance = builder.build()

                INSTANCE = instance
                instance
            }
        }
    }
}

// 타입 변환기 (필요한 경우)
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}