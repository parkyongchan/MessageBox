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
    // ⭐ 장비별 최신 위치 (필터 미적용)
    //   - 송신(0x00-0x03) + 수신(0x10-0x13) 모두 포함
    // ═══════════════════════════════════════════════════════

    @Transaction
    @Query("""
        SELECT * FROM locations
        WHERE create_at BETWEEN :startDate AND :endDate
          AND id IN (
              SELECT MAX(id) FROM locations
              WHERE create_at BETWEEN :startDate AND :endDate
              GROUP BY code_num
          )
        ORDER BY create_at DESC
    """)
    fun getLatestByDevice(
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>>


    // ═══════════════════════════════════════════════════════
    // ⭐ 필터 적용된 장비별 최신 위치
    //
    //   trackMode 값 (UI 필터):
    //     0 = ALL   (전체)
    //     2 = TRACK (0x01, 0x02, 0x03, 0x11, 0x12, 0x13)
    //     4 = SOS   (0x00, 0x10)
    //
    //   DB 에 저장된 track_mode 값 (프로토콜 ver):
    //     0  (0x00) = 내 SOS 송신
    //     1  (0x01) = 내 CAR TRACK 송신
    //     2  (0x02) = 내 UAV TRACK 송신 / 레거시 TRACK
    //     3  (0x03) = 내 UAT TRACK 송신
    //     4  (0x04) = 레거시 SOS
    //     5  (0x05) = 레거시 SOS
    //     16 (0x10) = 남 SOS 수신
    //     17 (0x11) = 남 CAR TRACK 수신
    //     18 (0x12) = 남 UAV TRACK 수신
    //     19 (0x13) = 남 UAT TRACK 수신
    // ═══════════════════════════════════════════════════════

    @Transaction
    @Query("""
        SELECT * FROM locations
        WHERE create_at BETWEEN :startDate AND :endDate
          AND (
              :trackMode = 0
              OR (:trackMode = 2 AND track_mode IN (1, 2, 3, 17, 18, 19))
              OR (:trackMode = 4 AND track_mode IN (0, 4, 5, 16))
          )
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
              WHERE create_at BETWEEN :startDate AND :endDate
                AND (
                    :trackMode = 0
                    OR (:trackMode = 2 AND track_mode IN (1, 2, 3, 17, 18, 19))
                    OR (:trackMode = 4 AND track_mode IN (0, 4, 5, 16))
                )
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


    // ═══════════════════════════════════════════════════════
    // ⭐ 특정 장비의 전체 트랙 (상세 화면용)
    //   - 시간 오름차순 (트랙 그리기 자연스러움)
    // ═══════════════════════════════════════════════════════

    @Transaction
    @Query("""
        SELECT * FROM locations 
        WHERE code_num = :codeNum
          AND create_at BETWEEN :startDate AND :endDate
        ORDER BY create_at ASC
    """)
    fun getTrackByDevice(
        codeNum: String,
        startDate: Date,
        endDate: Date
    ): LiveData<List<LocationWithAddress>>

}
