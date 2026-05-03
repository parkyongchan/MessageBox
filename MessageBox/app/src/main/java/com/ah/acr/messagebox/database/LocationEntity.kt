package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "locations",
    indices = [Index(value = ["dedup_hash", "received_at_ms"], name = "idx_loc_dedup")]
)
data class LocationEntity @JvmOverloads constructor(
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

    @ColumnInfo(name = "create_at")
    val createAt: Date?,

    @ColumnInfo(name = "is_read")        // app -> device
    var isRead: Boolean,
    @ColumnInfo(name = "is_send")        // app -> device
    var isSend: Boolean,
    @ColumnInfo(name = "is_device_send") // device -> server
    var isDeviceSend: Boolean,

    // ═══════════════════════════════════════════════════════
    // ⭐ v6 신규: 중복 수신 패킷 차단용
    // @JvmOverloads 덕분에 Java에서 기존 14개 인자 시그니처도 그대로 사용 가능
    // ═══════════════════════════════════════════════════════
    @ColumnInfo(name = "dedup_hash")
    var dedupHash: String? = null,
    @ColumnInfo(name = "received_at_ms")
    var receivedAtMs: Long = 0L
) {
    @Ignore
    var nicName: String? = null
    @Ignore
    var isChecked: Boolean? = false
}
