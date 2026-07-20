package com.ah.acr.messagebox.database

import androidx.room.*

@Dao
interface TacticalRecvDao {
    // Java에서 호출하기 쉽게 동기 메서드 (백그라운드 스레드에서 호출할 것)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: TacticalRecvEntity): Long

    @Query("SELECT * FROM tactical_recv ORDER BY recv_at ASC")
    fun getAllSync(): List<TacticalRecvEntity>

    @Query("DELETE FROM tactical_recv WHERE id = :id")
    fun deleteById(id: Int)

    @Query("DELETE FROM tactical_recv")
    fun deleteAll()
    @Query("DELETE FROM tactical_recv WHERE COALESCE(from_imei,'') = :imei")
    fun deleteByFromImei(imei: String)

    @Query("SELECT COUNT(*) FROM tactical_recv")
    fun count(): Int
}