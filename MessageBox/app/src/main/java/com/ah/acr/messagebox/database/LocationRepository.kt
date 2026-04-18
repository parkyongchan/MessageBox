package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import java.util.Date

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


    // ═══════════════════════════════════════════════════════
    // ⭐ 신규: 조회/필터/트랙 메서드
    // ═══════════════════════════════════════════════════════

    fun getLatestByDevice(
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>> {
        return locationDao.getLatestByDevice(startDate, endDate)
    }

    fun getFilteredLatest(
        startDate: Date,
        endDate: Date,
        trackMode: Int,
        search: String
    ): LiveData<List<LocationWithAddress>> {
        return locationDao.getFilteredLatest(startDate, endDate, trackMode, search)
    }

    fun getTrackByDevice(
        codeNum: String,
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>> {
        return locationDao.getTrackByDevice(codeNum, startDate, endDate)
    }
}
