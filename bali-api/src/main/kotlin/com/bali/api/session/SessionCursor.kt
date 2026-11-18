package com.bali.api.session

import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

// 세션 목록 무한스크롤 커서. 같은 date에 세션이 여러 개일 수 있어 (date, sortTime, id) 튜플을 쓴다.
// sortTime은 COALESCE(completedAt, createdAt)이며, 구버전 커서(date|id)는 sortTime이 null로 디코딩된다
data class SessionCursor(val date: LocalDate, val sortTime: Instant?, val id: UUID) {
    fun encode(): String {
        require(sortTime != null) { "새 커서는 sortTime이 필요하다" }
        return Base64.getUrlEncoder().withoutPadding().encodeToString("$date|$sortTime|$id".toByteArray())
    }

    companion object {
        // 클라이언트가 보낸 불투명 문자열을 디코딩. 2필드(구버전 date|id)와 3필드(date|sortTime|id)를 모두 받는다.
        // 형식이 깨져 있으면 시스템 경계(사용자 입력)이므로 400으로 매핑되는 IllegalArgumentException을 던진다
        fun decode(value: String): SessionCursor = try {
            val parts = String(Base64.getUrlDecoder().decode(value)).split("|")
            when (parts.size) {
                2 -> SessionCursor(LocalDate.parse(parts[0]), null, UUID.fromString(parts[1]))
                3 -> SessionCursor(LocalDate.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]))
                else -> throw IllegalArgumentException()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("유효하지 않은 cursor: $value")
        }
    }
}
