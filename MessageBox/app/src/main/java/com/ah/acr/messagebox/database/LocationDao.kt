package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

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


    // ═══════════════════════════════════════════════════════
    // ⭐ 신규: 조회/필터/트랙 쿼리
    // ═══════════════════════════════════════════════════════

    /**
     * 장비별 최신 위치 (메인 목록용)
     * - 수신된 위치만 (is_income_loc = 0)
     * - 장비 번호(code_num)별로 그룹핑
     * - 각 그룹에서 가장 최근 것만 선택
     */
    @Transaction
    @Query("""
        SELECT * FROM locations
        WHERE is_income_loc = 0
          AND create_at BETWEEN :startDate AND :endDate
          AND id IN (
              SELECT MAX(id) FROM locations
              WHERE is_income_loc = 0
                AND create_at BETWEEN :startDate AND :endDate
              GROUP BY code_num
          )
        ORDER BY create_at DESC
    """)
    fun getLatestByDevice(
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>>


    /**
     * 필터 적용된 장비별 최신 위치
     * - 서브쿼리로 주소록 검색 처리 (JOIN 없이)
     * @param trackMode 0=All, 2=Track, 4=SOS
     * @param search 검색어 (codeNum 또는 numbersNic 매칭)
     */
    @Transaction
    @Query("""
        SELECT * FROM locations
        WHERE is_income_loc = 0
          AND create_at BETWEEN :startDate AND :endDate
          AND (:trackMode = 0 OR track_mode = :trackMode)
          AND (
              :search = '' 
              OR code_num LIKE '%' || :search || '%'
              OR code_num IN (
                  SELECT numbers FROM address 
                  WHERE numbers_nic LIKE '%' || :search || '%'
              )
          )
          AND id IN (
              SELECT MAX(id) FROM locations
              WHERE is_income_loc = 0
                AND create_at BETWEEN :startDate AND :endDate
                AND (:trackMode = 0 OR track_mode = :trackMode)
              GROUP BY code_num
          )
        ORDER BY create_at DESC
    """)
    fun getFilteredLatest(
        startDate: Date,
        endDate: Date,
        trackMode: Int,
        search: String
    ): LiveData<List<LocationWithAddress>>


    /**
     * 특정 장비의 전체 트랙 (상세 화면용)
     * - 시간 오름차순 (트랙 그릴 때 자연스러움)
     */
    @Transaction
    @Query("""
        SELECT * FROM locations 
        WHERE code_num = :codeNum
          AND is_income_loc = 0
          AND create_at BETWEEN :startDate AND :endDate
        ORDER BY create_at ASC
    """)
    fun getTrackByDevice(
        codeNum: String,
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>>

}