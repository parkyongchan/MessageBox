package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "address",
    indices = [Index(value = ["numbers"], unique = true)])
data class AddressEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "numbers")
    val numbers: String,
    @ColumnInfo(name="numbers_nic")
    var numbersNic: String?,

    @ColumnInfo(name="create_at")
    val createAt: Date?,
    @ColumnInfo(name="edit_at")
    var editAt: Date?,
) {
    @Ignore
    var isChecked: Boolean? = false
}