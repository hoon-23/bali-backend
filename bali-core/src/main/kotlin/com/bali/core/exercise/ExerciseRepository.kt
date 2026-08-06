package com.bali.core.exercise

import java.util.UUID

// 운동 종목 영속성을 위한 포트 인터페이스
interface ExerciseRepository {
    // 고유 식별자로 종목을 조회, 없으면 null 반환
    fun findById(id: UUID): Exercise?

    // userId가 볼 수 있는 종목 목록 조회 (GLOBAL 전체 + 본인 PERSONAL)
    fun findVisibleTo(userId: UUID): List<Exercise>

    // 고유 식별자로 종목을 조회하되, userId가 볼 수 있는(GLOBAL 전체 + 본인 PERSONAL) 종목이 아니면 null 반환
    fun findVisibleTo(id: UUID, userId: UUID): Exercise?

    // 이름 유사도(trigram) 기준으로 정렬된 상위 종목 제안
    fun suggest(query: String, userId: UUID, limit: Int = 10): List<Exercise>

    // 종목을 저장하고 저장된 종목을 반환
    fun save(exercise: Exercise): Exercise
}
