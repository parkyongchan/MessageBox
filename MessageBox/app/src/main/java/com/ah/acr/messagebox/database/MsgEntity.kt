package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "messages")
data class MsgEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "is_send_msg")   // true:send, false:receive
    val isSendMsg: Boolean,

    @ColumnInfo(name = "code_num")
    val codeNum: String?,
    @ColumnInfo(name = "title")
    var title: String?,
    @ColumnInfo(name = "msg")
    var msg: String?,
    @ColumnInfo(name="create_at")
    val createAt: Date?,

    @ColumnInfo(name="receive_at")
    var receiveAt: Date?,
    @ColumnInfo(name="send_device_at")
    var sendDeviceAt: Date?,

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