package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxMsg (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "receiver")
    val receiver: String?,
    @ColumnInfo(name="sender")
    val sender: String?,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "msg")
    val msg: String?,
    @ColumnInfo(name = "is_send")
    val isSend: Boolean
) {
    @Ignore
    var isChecked: Boolean? = false
}