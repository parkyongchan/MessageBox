package com.ah.acr.messagebox.database

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import java.util.Date

class LocationRepository(private val locationDao: LocationDao) {

    companion object {
        // ⭐ v6: 중복 판별 시간 윈도우 (단말 재전송 주기 가정)
        private const val DEDUP_WINDOW_MS = 30_000L
        private const val TAG = "LocationRepository"
    }

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

    // ═════════════════════════════════════════════════════════
    //   ⭐ v6 중복 수신 차단 (2026-05-03)
    // ═════════════════════════════════════════════════════════
    suspend fun insertWithDedup(location: LocationEntity): InsertResult {
        // 앱→단말 송신은 dedup 제외
        if (!location.isIncomeLoc) {
            val id = locationDao.insertLocation(location)
            return InsertResult.Inserted(id)
        }

        val hash = DedupHasher.computeLocationHash(location)
        val now = System.currentTimeMillis()
        val windowStart = now - DEDUP_WINDOW_MS

        if (locationDao.countLocByHashSince(hash, windowStart) > 0) {
            Log.d(TAG, "Dup loc skip: codeNum=${location.codeNum} hash=${hash.take(8)}")
            return InsertResult.Duplicate
        }

        location.dedupHash = hash
        location.receivedAtMs = now
        val id = locationDao.insertLocation(location)
        return InsertResult.Inserted(id)
    }

    // ═════════════════════════════════════════════════════════
    //   ⭐ v6 일괄 삭제 (2026-05-03)
    // ═════════════════════════════════════════════════════════

    /** 특정 장비의 모든 트랙 삭제 */
    suspend fun deleteAllByCodeNum(codeNum: String): Int {
        val deletedCount = locationDao.deleteAllByCodeNum(codeNum)
        Log.d(TAG, "Deleted all tracks for $codeNum: $deletedCount rows")
        return deletedCount
    }

    /** 특정 장비의 특정 기간 트랙 삭제 */
    suspend fun deleteByCodeNumInRange(
        codeNum: String,
        startDate: Date,
        endDate: Date
    ): Int {
        val deletedCount = locationDao.deleteByCodeNumInRange(codeNum, startDate, endDate)
        Log.d(TAG, "Deleted tracks for $codeNum in range: $deletedCount rows")
        return deletedCount
    }

    /** 모든 트랙 삭제 (전체 초기화) */
    suspend fun deleteAll(): Int {
        val deletedCount = locationDao.deleteAll()
        Log.d(TAG, "Deleted ALL locations: $deletedCount rows")
        return deletedCount
    }
}
