package com.bali.core.exercise

import java.util.UUID

// 운동 종목 영속성을 위한 포트 인터페이스
interface ExerciseRepository {
    // 고유 식별자로 종목을 조회, 없으면 null 반환
    fun findById(id: UUID): Exercise?

    // userId가 볼 수 있는 종목 목록 조회 (GLOBAL 전체 + 본인 PERSONAL, 소프트 삭제된 종목 제외)
    fun findVisibleTo(userId: UUID): List<Exercise>

    // 고유 식별자로 종목을 조회하되, userId가 볼 수 있는(GLOBAL 전체 + 본인 PERSONAL) 종목이 아니면 null 반환
    fun findVisibleTo(id: UUID, userId: UUID): Exercise?

    // 이름 유사도(trigram) 기준으로 정렬된 상위 종목 제안 (소프트 삭제된 종목 제외)
    fun suggest(query: String, userId: UUID, limit: Int = 10): List<Exercise>

    // 종목을 저장하고 저장된 종목을 반환
    fun save(exercise: Exercise): Exercise

    // 종목을 소프트 삭제 (deleted=true). 기록이 참조하는 종목도 안전하게 숨길 수 있다
    fun softDeleteById(id: UUID)
}

// exerciseIds에 대응하는 종목을 일괄 조회해 Map으로 만든다. 존재하지 않는 exerciseId가 있으면 예외
fun ExerciseRepository.findAllByIds(exerciseIds: Collection<UUID>): Map<UUID, Exercise> =
    exerciseIds.distinct().associateWith { exerciseId ->
        findById(exerciseId) ?: throw IllegalStateException("존재하지 않는 exerciseId: $exerciseId")
    }
