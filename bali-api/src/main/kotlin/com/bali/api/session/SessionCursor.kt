package com.bali.api.session

import java.time.LocalDate
import java.util.Base64
import java.util.UUID

// 세션 목록 무한스크롤 커서. 같은 date에 세션이 여러 개일 수 있어 date 단독으로는 순서가 불안정하므로 (date, id) 튜플을 쓴다
data class SessionCursor(val date: LocalDate, val id: UUID) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$date|$id".toByteArray())

    companion object {
        // 클라이언트가 보낸 불투명 문자열을 디코딩. 형식이 깨져 있으면 시스템 경계(사용자 입력)이므로 400으로 매핑되는 IllegalArgumentException을 던진다
        fun decode(value: String): SessionCursor = try {
            val (dateStr, idStr) = String(Base64.getUrlDecoder().decode(value)).split("|", limit = 2)
            SessionCursor(LocalDate.parse(dateStr), UUID.fromString(idStr))
        } catch (e: Exception) {
            throw IllegalArgumentException("유효하지 않은 cursor: $value")
        }
    }
}
