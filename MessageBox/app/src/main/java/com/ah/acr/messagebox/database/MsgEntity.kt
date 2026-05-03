package com.ah.acr.messagebox.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [Index(value = ["dedup_hash", "received_at_ms"], name = "idx_msg_dedup")]
)
data class MsgEntity @JvmOverloads constructor(
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
    @ColumnInfo(name = "create_at")
    val createAt: Date?,

    @ColumnInfo(name = "receive_at")
    var receiveAt: Date?,
    @ColumnInfo(name = "send_device_at")
    var sendDeviceAt: Date?,

    @ColumnInfo(name = "is_read")        // app -> device
    var isRead: Boolean,
    @ColumnInfo(name = "is_send")        // app -> device
    var isSend: Boolean,
    @ColumnInfo(name = "is_device_send") // device -> server
    var isDeviceSend: Boolean,

    // ═══════════════════════════════════════════════════════
    // ⭐ v6 신규: 중복 수신 패킷 차단용
    // @JvmOverloads 덕분에 Java에서 기존 11개 인자 시그니처도 그대로 사용 가능
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
