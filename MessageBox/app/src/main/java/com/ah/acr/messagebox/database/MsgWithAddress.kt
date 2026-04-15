package com.ah.acr.messagebox.database


import androidx.room.Embedded
import androidx.room.Relation

data class MsgWithAddress(
    @Embedded val msg: MsgEntity,
    @Relation(
        parentColumn = "code_num",
        entityColumn = "numbers"
    )
    val address: AddressEntity ?
)
