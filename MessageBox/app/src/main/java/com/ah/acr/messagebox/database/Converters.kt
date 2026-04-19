package com.ah.acr.messagebox.database

import androidx.room.TypeConverter
import java.util.Date


/**
 * Room TypeConverters for fields that Room doesn't natively support.
 *
 * Date ↔ Long (timestamp in milliseconds)
 */
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
