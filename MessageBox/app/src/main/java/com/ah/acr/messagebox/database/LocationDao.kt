package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)


    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationFromId(id: Int): LocationEntity?

    @Query("SELECT * FROM locations ORDER BY id DESC")
    fun getAllLocationsFlow(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY id DESC")
    fun getAllLocations(): LiveData<List<LocationEntity>>

    @Transaction
    @Query("SELECT * FROM locations ORDER BY id DESC")
    fun getAllLocationAddress(): LiveData<List<LocationWithAddress>>


    @Query("Update locations SET is_send = 1 WHERE id = :id")
    suspend fun updateLocationSended(id: Int)

    @Query("Update locations SET is_device_send = 1 WHERE id = :id")
    suspend fun updateLocationDeviceSended(id: Int)

    @Query("Update locations SET is_read = 1 WHERE id = :id")
    suspend fun updateLocationReaded(id: Int)


    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocationById(id: Int)




}