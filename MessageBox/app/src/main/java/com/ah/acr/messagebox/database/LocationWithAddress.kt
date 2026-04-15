package com.ah.acr.messagebox.database


import androidx.room.Embedded
import androidx.room.Relation

data class LocationWithAddress(
    @Embedded val location: LocationEntity,
    @Relation(
        parentColumn = "code_num",
        entityColumn = "numbers"
    )
    val address: AddressEntity ?
)
