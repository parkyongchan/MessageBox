package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAddress(addr: AddressEntity): Long

    @Update
    suspend fun updateAddress(addr: AddressEntity)

    @Delete
    suspend fun deleteAddress(addr: AddressEntity)


    @Query("SELECT * FROM address WHERE id = :id")
    suspend fun getAddressFromId(id: Int): AddressEntity?

    @Query("SELECT * FROM address WHERE numbers = :numbers")
    suspend fun getAddressFromNumbers(numbers: String): AddressEntity?

    @Query("SELECT * FROM address WHERE numbers_nic = :nicName")
    suspend fun getAddressFromNicName(nicName: String): AddressEntity?



    @Query("SELECT * FROM address ORDER BY numbers_nic ASC")
    fun getAllAddressFlow(): Flow<List<AddressEntity>>

    @Query("SELECT * FROM address ORDER BY numbers_nic ASC")
    fun getAllAddress(): LiveData<List<AddressEntity>>

    @Query("SELECT * FROM address WHERE numbers_nic LIKE :searchQuery OR numbers LIKE :searchQuery")
    fun searchAddressByName(searchQuery: String): List<AddressEntity>


    @Query("Update address SET numbers_nic = :numbersNic WHERE numbers = :numbers")
    suspend fun updateNumbersNic(numbers: String, numbersNic: String)

    // ⭐ NEW: Update avatar path for given IMEI (numbers)
    @Query("UPDATE address SET avatar_path = :path WHERE numbers = :numbers")
    suspend fun updateAvatarPath(numbers: String, path: String?)

    @Query("DELETE FROM address WHERE id = :id")
    suspend fun deleteAddressById(id: Int)

    @Query("DELETE FROM address WHERE numbers = :numbers")
    suspend fun deleteAddressByNumbers(numbers: String)

}
