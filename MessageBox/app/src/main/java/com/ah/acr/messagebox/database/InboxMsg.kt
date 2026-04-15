package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName="inbox")
data class InboxMsg(
    @PrimaryKey
    val id: Int = 0,
    @ColumnInfo(name = "serial")
    val serial: String,
    @ColumnInfo(name = "sender")
    val sender: String?,
    @ColumnInfo(name="title")
    val title: String?,
    @ColumnInfo(name = "msg")
    val msg: String,
    @ColumnInfo(name = "is_new")
    val isNew: Boolean,
)
