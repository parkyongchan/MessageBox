package com.ah.acr.messagebox.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OutBoxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMsg(outBox: OutboxMsg)

    @Update
    suspend fun updateMsg(outBox: OutboxMsg)

    @Query("Update outbox SET is_send = 1 WHERE id = :id")
    suspend fun updateMsgSended(id: Int)

    @Delete
    suspend fun deleteMsg(outBox: OutboxMsg)

    @Query("DELETE FROM outbox")
    suspend fun deleteAllMsg()

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    fun getOutBoxAll(): Flow<List<OutboxMsg>>

    @Query("SELECT * FROM outbox WHERE id = :id")
    fun getOutBoxMsg(id: Int): Flow<OutboxMsg>

}