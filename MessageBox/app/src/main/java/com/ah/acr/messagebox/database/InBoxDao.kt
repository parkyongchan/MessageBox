package com.ah.acr.messagebox.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InBoxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMsg(inBox: InboxMsg)

    @Update
    suspend fun updateMsg(inBox: InboxMsg)

    @Query ("UPDATE inbox SET is_new = 0 WHERE id = :id")
    suspend fun updateMsgReaded(id: Int)

    @Delete
    suspend fun deleteMsg(inBox: InboxMsg)

    @Query("DELETE FROM inbox")
    suspend fun deleteAllMsg()

    @Query("SELECT * FROM inbox ORDER BY id ASC")
    fun getInBoxAll(): Flow<List<InboxMsg>>

    @Query("SELECT * FROM inbox WHERE id = :id")
    fun getInBoxMsg(id: Int): Flow<InboxMsg>

}