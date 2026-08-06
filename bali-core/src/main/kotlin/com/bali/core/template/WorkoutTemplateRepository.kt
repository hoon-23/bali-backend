package com.bali.core.template

import java.util.UUID

// 운동 템플릿 영속성을 위한 포트 인터페이스
interface WorkoutTemplateRepository {
    // 고유 식별자로 템플릿을 조회 (소프트 삭제된 템플릿은 null 반환)
    fun findById(id: UUID): WorkoutTemplate?

    // 특정 유저의 템플릿 목록 조회 (소프트 삭제된 템플릿 제외)
    fun findAllByUserId(userId: UUID): List<WorkoutTemplate>

    // 템플릿을 저장. 신규면 삽입, 기존이면 items를 전체 교체
    fun save(template: WorkoutTemplate): WorkoutTemplate

    // 템플릿을 소프트 삭제 (deleted=true)
    fun softDelete(id: UUID)
}
