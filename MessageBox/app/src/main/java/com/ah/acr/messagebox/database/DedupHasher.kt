package com.ah.acr.messagebox.database

import java.security.MessageDigest
import java.util.Locale

/**
 * 중복 수신 패킷 차단을 위한 hash 계산 유틸.
 *
 * 단말이 RECEIVED=N,OK 응답을 못 받으면 같은 메시지를 재전송하므로,
 * payload 내용을 SHA-256으로 hash하여 중복 판별 키로 사용.
 *
 * 윈도우(30초) 내에 같은 hash가 들어오면 중복으로 간주하여 무시.
 */
object DedupHasher {

    /**
     * 메시지 hash 키 = isSendMsg + codeNum + title + msg + createAt(epoch ms)
     *
     * - isSendMsg 포함: 우연히 같은 내용을 송수신하는 경우 분리
     * - createAt 포함: 단말 송신 시각 (재전송이어도 동일 값)
     */
    fun computeMsgHash(e: MsgEntity): String {
        val key = buildString {
            append(if (e.isSendMsg) "S" else "R").append('|')
            append(e.codeNum.orEmpty()).append('|')
            append(e.title.orEmpty()).append('|')
            append(e.msg.orEmpty()).append('|')
            append(e.createAt?.time ?: 0L)
        }
        return sha256(key)
    }

    /**
     * 위치 hash 키 = codeNum + trackMode + latitude(5자리) + longitude(5자리) + gpsDate(epoch ms)
     *
     * - lat/lng 5자리 = 약 1m 정밀도 (GPS 노이즈 흡수)
     * - gpsDate 포함: 단말 GPS 시각 (재전송이어도 동일 값)
     * - altitude/direction/speed 제외: GPS 노이즈로 ±1 흔들림 가능
     */
    fun computeLocationHash(e: LocationEntity): String {
        val key = String.format(
            Locale.US,
            "%s|%d|%.5f|%.5f|%d",
            e.codeNum.orEmpty(),
            e.trackMode,
            e.latitude ?: 0.0,
            e.longitude ?: 0.0,
            e.gpsDate?.time ?: 0L
        )
        return sha256(key)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
