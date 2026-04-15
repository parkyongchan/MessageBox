package com.ah.acr.messagebox.database

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.room.ColumnInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository
    val allLocations: LiveData<List<LocationEntity>>
    val allLocationAddress: LiveData<List<LocationWithAddress>>

    init {
        val locationDao = MsgRoomDatabase.getDatabase(application).locationDao()
        repository = LocationRepository(locationDao)
        allLocations = repository.allLocations
        allLocationAddress = repository.allLocationAddress
    }


    // 메세지 추가
    fun insert(location: LocationEntity) = viewModelScope.launch {
        try {
            repository.insert(location)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 추가 실패", e)
        }
    }

    fun insert(location: LocationEntity, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            repository.insert(location)
            onComplete(true)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 추가 실패", e)
            onComplete(false)
        }
    }

    // 메세지 업데이트
    fun update(location: LocationEntity) = viewModelScope.launch {
        try {
            repository.update(location)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 업데이트 실패", e)
        }
    }

    // 메세지 삭제
    fun delete(location: LocationEntity) = viewModelScope.launch {
        try {
            repository.delete(location)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 삭제 실패", e)
        }
    }

    fun getLocationById(id: Int): LiveData<LocationEntity?> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getLocationById(id))
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치; 조회 실패", e)
            emit(null)
        }
    }

//    fun getMsgById(msgId: Int): LiveData<Result<MsgEntity?>> = liveData {
//        try {
//            val message = repository.getMsgById(msgId)
//            emit(Result.success(message))
//        } catch (e: Exception) {
//            Log.e("MsgViewModel", "메세지 조회 실패: msgId=$msgId", e)
//            emit(Result.failure(e))
//        }
//    }

    fun updateRead(id: Int) = viewModelScope.launch {
        try {
            repository.updateLocationRead(id)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 Read update 실패", e)
        }
    }

    fun updateSend(id: Int) = viewModelScope.launch {
        try {
            repository.updateLocationSend(id)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 Send Update 실패", e)
        }
    }

    fun updateDeviceSend(id: Int) = viewModelScope.launch {
        try {
            repository.updateLocationDeviceSend(id)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 Device Send Update 실패", e)
        }
    }


}

class LocationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocationViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}