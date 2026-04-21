package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface MsgDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMsg(msg: MsgEntity): Long

    @Update
    suspend fun updateMsg(msg: MsgEntity)

    @Delete
    suspend fun deleteMsg(msg: MsgEntity)

    @Query("SELECT * FROM messages WHERE id = :msgId")
    suspend fun getMsgFromId(msgId: Int): MsgEntity?

    // ⭐ 변경: id 순 → create_at 순 (UTC 기반 발신 시각)
    @Query("SELECT * FROM messages ORDER BY create_at ASC, id ASC")
    fun getAllMsgsFlow(): Flow<List<MsgEntity>>

    // ⭐ 변경: 최신 메시지가 위에 오도록 (대화방 목록용)
    @Transaction
    @Query("SELECT * FROM messages ORDER BY create_at DESC, id DESC")
    fun getAllMsgAddress(): LiveData<List<MsgWithAddress>>

    // ⭐ 변경: 시간순 정렬
    @Query("SELECT * FROM messages ORDER BY create_at ASC, id ASC")
    fun getAllMsgs(): LiveData<List<MsgEntity>>

    @Query("SELECT * FROM messages ORDER BY create_at ASC, id ASC")
    fun getAllMsg(): List<MsgEntity>

    @Query("Update messages SET is_send = 1 WHERE id = :msgId")
    suspend fun updateMsgSended(msgId: Int)

    @Query("Update messages SET is_device_send = 1 WHERE id = :msgId")
    suspend fun updateMsgDeviceSended(msgId: Int)

    @Query("Update messages SET is_read = 1 WHERE id = :msgId")
    suspend fun updateMsgReaded(msgId: Int)

    @Query("DELETE FROM messages WHERE id = :msgId")
    suspend fun deleteMsgById(msgId: Int)

    // ⭐ 연락처별 마지막 메시지 (create_at 기준)
    @Transaction
    @Query("""
        SELECT * FROM messages 
        WHERE id IN (
            SELECT id FROM messages m1 
            WHERE create_at = (
                SELECT MAX(create_at) FROM messages m2 WHERE m2.code_num = m1.code_num
            )
        )
        ORDER BY create_at DESC
    """)
    fun getLastMsgPerContact(): LiveData<List<MsgWithAddress>>

    // ⭐ 특정 연락처와 대화 내역 (create_at 순 정렬)
    @Transaction
    @Query("SELECT * FROM messages WHERE code_num = :codeNum ORDER BY create_at ASC, id ASC")
    fun getMsgsByContact(codeNum: String): LiveData<List<MsgWithAddress>>

    // 읽지 않은 메시지 수
    @Query("SELECT COUNT(*) FROM messages WHERE code_num = :codeNum AND is_read = 0 AND is_send_msg = 0")
    fun getUnreadCount(codeNum: String): LiveData<Int>


    // ═════════════════════════════════════════════════════════
    //   ⭐ 자기 에코 매칭 쿼리
    // ═════════════════════════════════════════════════════════

    /**
     * 자기 자신이 보낸 메시지가 위성 경유하여 되돌아온 경우
     * 매칭되는 송신 레코드 찾기
     *
     * 조건:
     * - codeNum 일치 (수신자 IMEI = 내 IMEI)
     * - 메시지 내용 일치
     * - isSendMsg = true (내가 보낸 것)
     * - receiveAt IS NULL (아직 에코 수신 안 됨)
     *
     * 가장 최근 것 1개 반환
     */
    @Query("""
        SELECT * FROM messages 
        WHERE code_num = :codeNum
          AND msg = :message
          AND is_send_msg = 1
          AND receive_at IS NULL
        ORDER BY create_at DESC
        LIMIT 1
    """)
    suspend fun findSelfSentMessage(codeNum: String, message: String): MsgEntity?


    /**
     * 자기 에코 수신 처리:
     * - receiveAt 기록 (수신 시각)
     * - isSend = true (전송 완료 처리)
     */
    @Query("""
        UPDATE messages 
        SET receive_at = :receiveAt, is_send = 1 
        WHERE id = :msgId
    """)
    suspend fun markSelfEchoReceived(msgId: Int, receiveAt: Date)

}
