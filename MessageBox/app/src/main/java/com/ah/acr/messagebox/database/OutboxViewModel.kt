package com.ah.acr.messagebox.database

import androidx.lifecycle.*
import androidx.room.ColumnInfo
import kotlinx.coroutines.launch

class OutboxViewModel (private val outBoxDao: OutBoxDao) : ViewModel() {

    val allOutboxMsgs: LiveData<List<OutboxMsg>> = outBoxDao.getOutBoxAll().asLiveData()

    fun updateOutboxMsg(id: Int, receiver: String, sender: String, title: String, msg: String, isSend: Boolean) {
        val updateOutboxMsg = getUpdatedOutBoxMsgEntry(id, receiver, sender, title, msg, isSend)
        updateOutboxMsg(updateOutboxMsg)
    }

    private fun updateOutboxMsg(outbox: OutboxMsg) {
        viewModelScope.launch {
            outBoxDao.updateMsg(outbox)
        }
    }

    fun updateOutboxMsgSended(id: Int) {
        updateOutboxMsgSend(id)
    }
    private fun updateOutboxMsgSend(id: Int) {
        viewModelScope.launch {
            outBoxDao.updateMsgSended(id)
        }
    }

    fun addNewOutboxMsg(receiver: String, sender: String, title: String, msg: String, isSend: Boolean) {
        val newItem = getNewOutboxMsgEntry(receiver, sender, title, msg, isSend)
        insertOutboxMsg(newItem)
    }

    private fun insertOutboxMsg(outbox: OutboxMsg) {
        viewModelScope.launch {
            outBoxDao.insertMsg(outbox)
        }
    }

    fun deleteOutboxMsg(outbox: OutboxMsg) {
        viewModelScope.launch {
            outBoxDao.deleteMsg(outbox)
        }
    }

    fun deleteAllOutboxMsg() {
        viewModelScope.launch {
            outBoxDao.deleteAllMsg()
        }
    }

    fun retrieveOutboxMsg(id: Int): LiveData<OutboxMsg> {
        return outBoxDao.getOutBoxMsg(id).asLiveData()
    }

    fun isEntryValid(receiver: String, msg: String): Boolean {
        if (receiver.isBlank() || msg.isBlank()) {
            return false
        }
        return true
    }

    private fun getNewOutboxMsgEntry(receiver: String, sender: String, title: String, msg: String, isSend: Boolean)
    : OutboxMsg {
        return OutboxMsg(receiver = receiver, sender = sender, title = title, msg=msg, isSend = isSend)
    }

    private fun getUpdatedOutBoxMsgEntry(
        id: Int,
        receiver: String,
        sender: String,
        title: String,
        msg: String,
        isSend: Boolean
    ): OutboxMsg {
        return OutboxMsg(id = id, receiver = receiver, sender = sender, title = title, msg= msg, isSend = isSend)
    }
}

class OutboxViewModelFactory(private val outBoxDao: OutBoxDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OutboxViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OutboxViewModel(outBoxDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}