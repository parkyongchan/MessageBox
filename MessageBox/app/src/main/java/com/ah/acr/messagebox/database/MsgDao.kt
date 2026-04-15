package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun getAllMsgsFlow(): Flow<List<MsgEntity>>

    @Transaction
    @Query("SELECT * FROM messages ORDER BY id DESC")
    fun getAllMsgAddress(): LiveData<List<MsgWithAddress>>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun getAllMsgs(): LiveData<List<MsgEntity>>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun getAllMsg(): List<MsgEntity>

    @Query("Update messages SET is_send = 1 WHERE id = :msgId")
    suspend fun updateMsgSended(msgId: Int)

    @Query("Update messages SET is_device_send = 1 WHERE id = :msgId")
    suspend fun updateMsgDeviceSended(msgId: Int)

    @Query("Update messages SET is_read = 1 WHERE id = :msgId")
    suspend fun updateMsgReaded(msgId: Int)

    @Query("DELETE FROM messages WHERE id = :msgId")
    suspend fun deleteMsgById(msgId: Int)

    // 연락처별 마지막 메시지 (대화방 목록용)
    @Transaction
    @Query("""
        SELECT * FROM messages 
        WHERE id IN (
            SELECT MAX(id) FROM messages GROUP BY code_num
        )
        ORDER BY id DESC
    """)
    fun getLastMsgPerContact(): LiveData<List<MsgWithAddress>>

    // 특정 연락처와 대화 내역
    @Transaction
    @Query("SELECT * FROM messages WHERE code_num = :codeNum ORDER BY id ASC")
    fun getMsgsByContact(codeNum: String): LiveData<List<MsgWithAddress>>

    // 읽지 않은 메시지 수
    @Query("SELECT COUNT(*) FROM messages WHERE code_num = :codeNum AND is_read = 0 AND is_send_msg = 0")
    fun getUnreadCount(codeNum: String): LiveData<Int>

}