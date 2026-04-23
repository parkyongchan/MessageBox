package com.ah.acr.messagebox.service

import android.content.Context
import android.util.Log
import com.ah.acr.messagebox.database.LocationEntity
import com.ah.acr.messagebox.database.MsgRoomDatabase
import kotlinx.coroutines.runBlocking

/**
 * ⭐ v4 Phase B-2-4-B: Service(Java)에서 LocationEntity 저장용 헬퍼
 *
 * 배경:
 * - LocationDao.insertLocation은 Kotlin suspend fun
 * - Java에서 직접 호출 불가 (Coroutine 필요)
 * - runBlocking으로 동기 실행하여 Java에서 호출 가능하게 래핑
 *
 * 사용:
 * Java Service에서:
 *   long id = LocationDbHelper.insertFromService(
 *       getApplicationContext(), locationEntity);
 */
object LocationDbHelper {

    private const val TAG = "LocationDbHelper"

    /**
     * LocationEntity를 DB에 저장
     *
     * @param context applicationContext 권장
     * @param entity 저장할 LocationEntity
     * @return 저장된 row의 ID, 실패 시 -1
     *
     * 주의: runBlocking 사용 → 호출하는 스레드를 차단함
     *       반드시 백그라운드 스레드에서 호출하세요!
     */
    @JvmStatic
    fun insertFromService(context: Context, entity: LocationEntity): Long {
        return try {
            runBlocking {
                val db = MsgRoomDatabase.getDatabase(context)
                val id = db.locationDao().insertLocation(entity)
                Log.v(TAG, "✅ LocationEntity 저장 성공 (id=$id)")
                id
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠ LocationEntity 저장 실패: ${e.message}", e)
            -1L
        }
    }
}
