package com.ah.acr.messagebox.database

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class MsgViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MsgRepository
    val allMsgs: LiveData<List<MsgEntity>>
    val allMsgAddress: LiveData<List<MsgWithAddress>>
    val lastMsgPerContact: LiveData<List<MsgWithAddress>>

    init {
        val msgDao = MsgRoomDatabase.getDatabase(application).msgDao()
        repository = MsgRepository(msgDao)
        allMsgs = repository.allMsgs
        allMsgAddress = repository.allMsgAddress
        lastMsgPerContact = repository.lastMsgPerContact
    }

    fun getMsgsByContact(codeNum: String): LiveData<List<MsgWithAddress>> {
        return repository.getMsgsByContact(codeNum)
    }

    fun getUnreadCount(codeNum: String): LiveData<Int> {
        return repository.getUnreadCount(codeNum)
    }

    fun insert(msg: MsgEntity) = viewModelScope.launch {
        try {
            repository.insert(msg)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 추가 실패", e)
        }
    }

    fun insert(msg: MsgEntity, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            repository.insert(msg)
            onComplete(true)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 추가 실패", e)
            onComplete(false)
        }
    }

    fun update(msg: MsgEntity) = viewModelScope.launch {
        try {
            repository.update(msg)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 업데이트 실패", e)
        }
    }

    // ← 이것 추가됨
    fun update(msg: MsgEntity, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            repository.update(msg)
            onComplete(true)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 업데이트 실패", e)
            onComplete(false)
        }
    }

    fun delete(msg: MsgEntity) = viewModelScope.launch {
        try {
            repository.delete(msg)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 삭제 실패", e)
        }
    }

    fun getMsgById(msgId: Int): LiveData<MsgEntity?> = liveData(Dispatchers.IO) {
        try {
            emit(repository.getMsgById(msgId))
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 조회 실패", e)
            emit(null)
        }
    }

    fun updateRead(msgId: Int) = viewModelScope.launch {
        try {
            repository.updateMsgRead(msgId)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 Read update 실패", e)
        }
    }

    fun updateSend(msgId: Int) = viewModelScope.launch {
        try {
            repository.updateMsgSend(msgId)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 Send Update 실패", e)
        }
    }

    fun updateDeviceSend(msgId: Int) = viewModelScope.launch {
        try {
            repository.updateMsgDeviceSend(msgId)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 Device Send Update 실패", e)
        }
    }

    fun deleteById(id: Int) = viewModelScope.launch {
        try {
            repository.deleteById(id)
        } catch (e: Exception) {
            Log.e("MsgViewModel", "메세지 삭제 실패", e)
        }
    }


    // ═════════════════════════════════════════════════════════
    //   ⭐ 자기 에코 매칭
    // ═════════════════════════════════════════════════════════

    /**
     * 자기 에코 메시지 처리
     * - 자기 자신이 보낸 메시지가 위성 경유하여 되돌아온 경우
     * - 기존 송신 레코드를 찾아서 업데이트 (새 레코드 생성 X)
     *
     * @param codeNum 수신자 IMEI (= 내 IMEI)
     * @param message 메시지 본문
     * @param onResult 콜백: true = 매칭 성공 (기존 레코드 업데이트),
     *                       false = 매칭 실패 (중복이거나 이미 처리됨)
     */
    fun tryMarkSelfEcho(
        codeNum: String,
        message: String,
        onResult: (Boolean) -> Unit
    ) = viewModelScope.launch {
        try {
            val existing = repository.findSelfSentMessage(codeNum, message)
            if (existing != null) {
                // 기존 송신 레코드 발견 → 수신 완료 표시
                repository.markSelfEchoReceived(existing.id, Date())
                Log.v("SELF-ECHO", "매칭 성공: id=${existing.id} codeNum=$codeNum")
                onResult(true)
            } else {
                // 매칭 실패 (이미 처리된 에코 또는 다른 사람 메시지)
                Log.v("SELF-ECHO", "매칭 실패: codeNum=$codeNum (중복 에코)")
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e("MsgViewModel", "자기 에코 처리 실패", e)
            onResult(false)
        }
    }
}

class MsgViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MsgViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MsgViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
