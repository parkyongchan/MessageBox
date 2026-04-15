package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {

    val allLocations: LiveData<List<LocationEntity>> = locationDao.getAllLocations()
    val allLocationsFlow: Flow<List<LocationEntity>> = locationDao.getAllLocationsFlow()
    val allLocationAddress: LiveData<List<LocationWithAddress>> = locationDao.getAllLocationAddress()

    suspend fun insert(location: LocationEntity): Long {
        return locationDao.insertLocation(location)
    }

    suspend fun update(location: LocationEntity) {
        locationDao.updateLocation(location)
    }

    suspend fun delete(location: LocationEntity) {
        locationDao.deleteLocation(location)
    }

    suspend fun getLocationById(id: Int): LocationEntity? {
        return locationDao.getLocationFromId(id)
    }


    suspend fun updateLocationRead(id: Int) {
        locationDao.updateLocationReaded(id)
    }

    suspend fun updateLocationSend(id: Int) {
        locationDao.updateLocationSended(id)
    }

    suspend fun updateLocationDeviceSend(id: Int) {
        locationDao.updateLocationDeviceSended(id)
    }



    suspend fun deleteById(msgId: Int) {
        locationDao.deleteLocationById(msgId)
    }
}