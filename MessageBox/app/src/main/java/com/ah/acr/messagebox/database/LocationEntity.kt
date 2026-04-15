package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "locations")
data class LocationEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "is_income_loc")   // true:send, false:receive
    val isIncomeLoc: Boolean = true,
    @ColumnInfo(name = "track_mode")
    val trackMode: Int,
    @ColumnInfo(name = "code_num")
    val codeNum: String?,
    @ColumnInfo(name = "latitude")
    var latitude: Double?,
    @ColumnInfo(name = "longitude")
    var longitude: Double?,
    @ColumnInfo(name = "altitude")
    var altitude: Int?,
    @ColumnInfo(name = "direction")
    var direction: Int?,
    @ColumnInfo(name = "speed")
    var speed: Int?,
    @ColumnInfo(name = "gps_date")
    var gpsDate: Date?,

    @ColumnInfo(name="create_at")
    val createAt: Date?,

    @ColumnInfo(name = "is_read")   // app -> device
    var isRead: Boolean,
    @ColumnInfo(name = "is_send")   // app -> device
    var isSend: Boolean,
    @ColumnInfo(name = "is_device_send")  // device -> server
    var isDeviceSend: Boolean
) {
    @Ignore
    var nicName: String?=null
    @Ignore
    var isChecked: Boolean? = false

}