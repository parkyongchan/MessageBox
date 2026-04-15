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

    //@Query("SELECT m.*, a.numbers_nic as nicName  FROM messages m INNER JOIN address a ON m.code_num = a.numbers ORDER BY id ASC")
    //fun getAllMsgs(): LiveData<List<MsgEntity>>
    @Transaction
    @Query("SELECT *  FROM messages ORDER BY id DESC")
    fun getAllMsgAddress(): LiveData<List<MsgWithAddress>>

    @Query("SELECT *  FROM messages ORDER BY id ASC")
    fun getAllMsgs(): LiveData<List<MsgEntity>>

    @Query("SELECT *  FROM messages ORDER BY id ASC")
    fun getAllMsg(): List<MsgEntity>


    @Query("Update messages SET is_send = 1 WHERE id = :msgId")
    suspend fun updateMsgSended(msgId: Int)

    @Query("Update messages SET is_device_send = 1 WHERE id = :msgId")
    suspend fun updateMsgDeviceSended(msgId: Int)

    @Query("Update messages SET is_read = 1 WHERE id = :msgId")
    suspend fun updateMsgReaded(msgId: Int)


    @Query("DELETE FROM messages WHERE id = :msgId")
    suspend fun deleteMsgById(msgId: Int)




}