package com.ah.acr.messagebox.database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class MsgRepository(private val msgDao: MsgDao) {

    val allMsgs: LiveData<List<MsgEntity>> = msgDao.getAllMsgs()
    val allMsgsFlow: Flow<List<MsgEntity>> = msgDao.getAllMsgsFlow()
    val allMsgAddress: LiveData<List<MsgWithAddress>> = msgDao.getAllMsgAddress()


//    suspend fun getAllMsg(): List<MsgEntity> {
//        return msgDao.getAllMsg()
//    }

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