package com.ah.acr.messagebox.database

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AddressRepository
    val allAddress: LiveData<List<AddressEntity>>

    init {
        val addressDao = MsgRoomDatabase.getDatabase(application).addressDao()
        repository = AddressRepository(addressDao)
        allAddress = repository.allAddress
    }

    fun insert(addr: AddressEntity) = viewModelScope.launch {
        try {
            repository.insert(addr)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 추가 실패", e)
        }
    }

    fun update(addr: AddressEntity) = viewModelScope.launch {
        try {
            repository.update(addr)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 업데이트 실패", e)
        }
    }

    fun delete(addr: AddressEntity) = viewModelScope.launch {
        try {
            repository.delete(addr)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 삭제 실패", e)
        }
    }

    fun getAddressById(id: Int): LiveData<AddressEntity?> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getAddressById(id))
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 조회 실패", e)
            emit(null)
        }
    }

    fun getSearchAddress(query: String): LiveData<List<AddressEntity>> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getSearchAddress(query))
        } catch (e: Exception) {
            Log.e("AddressViewModel", "검색 실패")
            emit(emptyList())
        }
    }

    fun getAddressByNumbers(numbers: String): LiveData<AddressEntity?> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getAddressByNumbers(numbers))
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 조회 실패", e)
            emit(null)
        }
    }

    fun getAddressByNicName(nicName: String): LiveData<AddressEntity?> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getAddressByNicName(nicName))
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 조회 실패", e)
            emit(null)
        }
    }


    fun updateNumbersNic(numbers: String, numbersNic: String) = viewModelScope.launch {
        try {
            repository.updateNumbersNic(numbers, numbersNic)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 Read update 실패", e)
        }
    }

    // ⭐ NEW: Update avatar path for a contact
    fun updateAvatarPath(numbers: String, path: String?) = viewModelScope.launch {
        try {
            repository.updateAvatarPath(numbers, path)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "avatar path update 실패", e)
        }
    }

    fun deleteAddressByNumbers(numbers: String) = viewModelScope.launch {
        try {
            repository.deleteAddressByNumbers(numbers)
        } catch (e: Exception) {
            Log.e("AddressViewModel", "메세지 Send Update 실패", e)
        }
    }

}


class AddressViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddressViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddressViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
