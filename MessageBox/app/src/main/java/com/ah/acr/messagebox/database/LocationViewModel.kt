package com.ah.acr.messagebox.database

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository
    val allLocations: LiveData<List<LocationEntity>>
    val allLocationAddress: LiveData<List<LocationWithAddress>>


    // ═══════════════════════════════════════════════════════
    // 필터 상태 관리
    // ═══════════════════════════════════════════════════════

    private val _startDate = MutableLiveData<Date>(defaultStartDate())
    private val _endDate = MutableLiveData<Date>(Date())
    private val _searchText = MutableLiveData<String>("")
    private val _filterMode = MutableLiveData<Int>(0)


    val startDate: LiveData<Date> = _startDate
    val endDate: LiveData<Date> = _endDate
    val searchText: LiveData<String> = _searchText
    val filterMode: LiveData<Int> = _filterMode


    fun setDateRange(start: Date, end: Date) {
        _startDate.value = start
        _endDate.value = end
        refresh()
    }

    fun setStartDate(date: Date) {
        _startDate.value = date
        refresh()
    }

    fun setEndDate(date: Date) {
        _endDate.value = date
        refresh()
    }

    fun setSearchText(text: String) {
        _searchText.value = text
        refresh()
    }

    fun setFilterMode(mode: Int) {
        _filterMode.value = mode
        refresh()
    }

    fun setQuickRange(hours: Int) {
        val now = Date()
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.HOUR, -hours)
        setDateRange(cal.time, now)
    }

    fun setQuickDays(days: Int) {
        val now = Date()
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.DAY_OF_MONTH, -days)
        setDateRange(cal.time, now)
    }


    // ═══════════════════════════════════════════════════════
    // 메인 화면 조회
    // ═══════════════════════════════════════════════════════

    private val _filteredLocations = MediatorLiveData<List<LocationWithAddress>>()
    val filteredLocations: LiveData<List<LocationWithAddress>> = _filteredLocations

    private var currentQuerySource: LiveData<List<LocationWithAddress>>? = null


    init {
        val locationDao = MsgRoomDatabase.getDatabase(application).locationDao()
        repository = LocationRepository(locationDao)
        allLocations = repository.allLocations
        allLocationAddress = repository.allLocationAddress
        refresh()
    }


    fun refresh() {
        currentQuerySource?.let { _filteredLocations.removeSource(it) }

        val start = _startDate.value ?: defaultStartDate()
        val end = _endDate.value ?: Date()
        val search = _searchText.value ?: ""
        val mode = _filterMode.value ?: 0

        val newSource = repository.getFilteredLatest(start, end, mode, search)
        currentQuerySource = newSource

        _filteredLocations.addSource(newSource) { list ->
            _filteredLocations.value = list ?: emptyList()
        }
    }


    // ═══════════════════════════════════════════════════════
    // ⭐ 상세 팝업용 - 직접 조회
    // ═══════════════════════════════════════════════════════

    /** 특정 장비의 전체 트랙 조회 (메인 필터 무관) */
    fun getTrackByDevice(codeNum: String): LiveData<List<LocationWithAddress>> {
        val start = _startDate.value ?: defaultStartDate()
        val end = _endDate.value ?: Date()
        return repository.getTrackByDevice(codeNum, start, end)
    }

    /**
     * ⭐ 특정 장비의 특정 기간 트랙 조회 (상세 팝업 전용)
     * - 메인 화면의 필터 상태와 완전히 독립적
     */
    fun getTrackByDeviceDirect(
        codeNum: String,
        start: Date,
        end: Date
    ): LiveData<List<LocationWithAddress>> {
        return repository.getTrackByDevice(codeNum, start, end)
    }

    /** Repository 접근자 (null check용) */
    fun getRepository(): LocationRepository = repository


    private fun defaultStartDate(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -7)
        return cal.time
    }


    // ═══════════════════════════════════════════════════════
    // 기존 메서드
    // ═══════════════════════════════════════════════════════

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

    fun update(location: LocationEntity) = viewModelScope.launch {
        try {
            repository.update(location)
        } catch (e: Exception) {
            Log.e("LocationViewModel", "위치 업데이트 실패", e)
        }
    }

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
            Log.e("LocationViewModel", "위치 조회 실패", e)
            emit(null)
        }
    }

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
