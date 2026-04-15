package com.ah.acr.messagebox.database

import androidx.lifecycle.*
import androidx.room.ColumnInfo
import kotlinx.coroutines.launch
import java.util.ArrayList

class InboxViewModel(private val inBoxDao: InBoxDao) : ViewModel() {

    val allInboxMsgs: LiveData<List<InboxMsg>> = inBoxDao.getInBoxAll().asLiveData()




    fun updateInboxMsg(id: Int, serial: String, sender: String,  title: String, msg: String, isNew: Boolean) {
        val updateInboxMsg = getUpdatedInBoxMsgEntry(id, serial, sender, title, msg, isNew)
        updateInboxMsg(updateInboxMsg)
    }

    private fun updateInboxMsg(inbox: InboxMsg) {
        viewModelScope.launch {
            inBoxDao.updateMsg(inbox)
        }
    }

    fun updateInboxMsgRead(id: Int) {
        updateInboxMsgReaded(id)
    }
    private fun updateInboxMsgReaded(id: Int) {
        viewModelScope.launch {
            inBoxDao.updateMsgReaded(id)
        }
    }

    fun addNewInboxMsg(id: Int, serial: String, sender: String, title: String,  msg: String, isNew: Boolean) {
        val newItem = getNewInboxMsgEntry(id, serial, sender, title, msg, isNew)
        insertInboxMsg(newItem)
    }

    private fun insertInboxMsg(inbox: InboxMsg) {
        viewModelScope.launch {
            inBoxDao.insertMsg(inbox)
        }
    }

    fun deleteAllInboxMsg() {
        viewModelScope.launch {
            inBoxDao.deleteAllMsg()
        }
    }

    fun deleteInboxMsg(inbox: InboxMsg) {
        viewModelScope.launch {
            inBoxDao.deleteMsg(inbox)
        }
    }

    fun retrieveInboxMsg(id: Int): LiveData<InboxMsg> {
        return inBoxDao.getInBoxMsg(id).asLiveData()
    }

    fun isEntryValid(id: Int, serial: String, sender: String, title: String, msg: String): Boolean {
        if (id == 0 || sender.isBlank() || msg.isBlank()) {
            return false
        }
        return true
    }

    private fun getNewInboxMsgEntry(id: Int, serial: String, sender: String, title: String, msg: String,  isNew: Boolean)
        : InboxMsg {
        return InboxMsg(id = id, serial = serial, sender = sender, title = title, msg = msg, isNew = isNew)
    }

    private fun getUpdatedInBoxMsgEntry(
        id: Int,
        serial: String,
        sender: String,
        title: String,
        msg: String,
        isNew: Boolean
    ): InboxMsg {
        return InboxMsg(id = id, serial = serial, sender = sender, title = title, msg = msg, isNew = isNew)
    }
}

class InboxViewModelFactory(private val inBoxDao: InBoxDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InboxViewModel(inBoxDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}