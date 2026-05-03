package com.ah.acr.messagebox.database

/**
 * insertWithDedup() 결과 타입.
 *
 * - Inserted: 신규 저장 성공 (id 반환)
 * - Duplicate: 중복으로 판단되어 스킵됨
 */
sealed class InsertResult {
    data class Inserted(val id: Long) : InsertResult()
    object Duplicate : InsertResult()
}
