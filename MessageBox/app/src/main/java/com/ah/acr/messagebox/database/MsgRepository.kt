package com.ah.acr.messagebox.database

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import java.util.Date

class MsgRepository(private val msgDao: MsgDao) {

    companion object {
        // ⭐ v6: 중복 판별 시간 윈도우 (단말 재전송 주기 가정)
        private const val DEDUP_WINDOW_MS = 30_000L
        private const val TAG = "MsgRepository"
    }

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

    // ═════════════════════════════════════════════════════════
    //   ⭐ 자기 에코 매칭
    // ═════════════════════════════════════════════════════════
    /** 자기 에코 후보 찾기 (매칭되는 송신 레코드) */
    suspend fun findSelfSentMessage(codeNum: String, message: String): MsgEntity? {
        return msgDao.findSelfSentMessage(codeNum, message)
    }

    /** 자기 에코 수신 처리 */
    suspend fun markSelfEchoReceived(msgId: Int, receiveAt: Date) {
        msgDao.markSelfEchoReceived(msgId, receiveAt)
    }

    // ═════════════════════════════════════════════════════════
    //   ⭐ v6 중복 수신 차단 (2026-05-03)
    // ═════════════════════════════════════════════════════════
    /**
     * 중복 체크 후 저장.
     *
     * 동작:
     * 1. 송신 메시지(isSendMsg=true)는 dedup 제외 → 항상 insert
     *    (사용자가 같은 내용 반복 송신 가능)
     * 2. 수신 메시지는 hash + 30초 윈도우로 중복 체크
     * 3. 중복이면 Duplicate 반환, 아니면 hash + receivedAtMs 채워서 insert
     *
     * ⚠️ 자기 에코 매칭과의 관계:
     *   - 호출 순서: 자기 에코 매칭(tryMarkSelfEcho) → 실패 시에만 insertWithDedup 호출
     *   - 자기 에코는 update이므로 dedup과 무관
     *   - dedup은 "다른 사람이 보낸 메시지의 중복 수신"만 차단
     */
    suspend fun insertWithDedup(message: MsgEntity): InsertResult {
        // 송신 메시지는 dedup 제외 (사용자 반복 송신 허용)
        if (message.isSendMsg) {
            val id = msgDao.insertMsg(message)
            return InsertResult.Inserted(id)
        }

        val hash = DedupHasher.computeMsgHash(message)
        val now = System.currentTimeMillis()
        val windowStart = now - DEDUP_WINDOW_MS

        if (msgDao.countMsgByHashSince(hash, windowStart) > 0) {
            Log.d(TAG, "Dup msg skip: codeNum=${message.codeNum} hash=${hash.take(8)}")
            return InsertResult.Duplicate
        }

        message.dedupHash = hash
        message.receivedAtMs = now
        val id = msgDao.insertMsg(message)
        return InsertResult.Inserted(id)
    }
}
