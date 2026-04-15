package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class MsgRepository(private val msgDao: MsgDao) {

    val allMsgs: LiveData<List<MsgEntity>> = msgDao.getAllMsgs()
    val allMsgsFlow: Flow<List<MsgEntity>> = msgDao.getAllMsgsFlow()
    val allMsgAddress: LiveData<List<MsgWithAddress>> = msgDao.getAllMsgAddress()

    // 연락처별 마지막 메시지 (대화방 목록)
    val lastMsgPerContact: LiveData<List<MsgWithAddress>> = msgDao.getLastMsgPerContact()

    // 특정 연락처 대화 내역
    fun getMsgsByContact(codeNum: String): LiveData<List<MsgWithAddress>> {
        return msgDao.getMsgsByContact(codeNum)
    }

    // 읽지 않은 메시지 수
    fun getUnreadCount(codeNum: String): LiveData<Int> {
        return msgDao.getUnreadCount(codeNum)
    }

    suspend fun insert(msg: MsgEntity): Long {
        return msgDao.insertMsg(msg)
    }

    suspend fun update(msg: MsgEntity) {
        msgDao.updateMsg(msg)
    }

    suspend fun delete(msg: MsgEntity) {
        msgDao.deleteMsg(msg)
    }

    suspend fun getMsgById(msgId: Int): MsgEntity? {
        return msgDao.getMsgFromId(msgId)
    }

    suspend fun updateMsgRead(msgId: Int) {
        msgDao.updateMsgReaded(msgId)
    }

    suspend fun updateMsgSend(msgId: Int) {
        msgDao.updateMsgSended(msgId)
    }

    suspend fun updateMsgDeviceSend(msgId: Int) {
        msgDao.updateMsgDeviceSended(msgId)
    }

    suspend fun deleteById(msgId: Int) {
        msgDao.deleteMsgById(msgId)
    }
}