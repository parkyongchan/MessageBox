package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class AddressRepository(private val addressDao: AddressDao) {

    val allAddress: LiveData<List<AddressEntity>> = addressDao.getAllAddress()
    val allAddressFlow: Flow<List<AddressEntity>> = addressDao.getAllAddressFlow()

    suspend fun insert(addr: AddressEntity): Long {
        return addressDao.insertAddress(addr)
    }

    suspend fun update(location: AddressEntity) {
        addressDao.updateAddress(location)
    }

    suspend fun delete(location: AddressEntity) {
        addressDao.deleteAddress(location)
    }

    suspend fun getAddressById(id: Int): AddressEntity? {
        return addressDao.getAddressFromId(id)
    }

    suspend fun getAddressByNumbers(numbers: String): AddressEntity? {
        return addressDao.getAddressFromNumbers(numbers)
    }

    suspend fun getAddressByNicName(nicName: String): AddressEntity? {
        return addressDao.getAddressFromNicName(nicName)
    }

    suspend fun getSearchAddress(query: String): List<AddressEntity> {
        return addressDao.searchAddressByName("%$query%")
    }

    suspend fun updateNumbersNic(numbers: String, numbersNic: String) {
        addressDao.updateNumbersNic(numbers, numbersNic)
    }

    // ⭐ NEW: Update avatar path
    suspend fun updateAvatarPath(numbers: String, path: String?) {
        addressDao.updateAvatarPath(numbers, path)
    }

    suspend fun deleteAddressByNumbers(numbers: String) {
        addressDao.deleteAddressByNumbers(numbers)
    }

    suspend fun deleteById(id: Int) {
        addressDao.deleteAddressById(id)
    }
}
