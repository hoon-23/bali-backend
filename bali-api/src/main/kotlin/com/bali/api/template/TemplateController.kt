package com.bali.api.template

import com.bali.core.exercise.ExerciseRepository
import com.bali.core.template.TemplateItem
import com.bali.core.template.WorkoutTemplate
import com.bali.core.template.WorkoutTemplateRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 운동 템플릿 CRUD API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/templates")
class TemplateController(
    private val templateRepository: WorkoutTemplateRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    // 템플릿 등록 (items의 각 exerciseId 타입을 조회해 STRENGTH/CARDIO 필드 검증 후 저장)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TemplateCreateRequest): TemplateResponse {
        val items = request.items.map { it.toDomainItem() }
        val saved = templateRepository.save(
            WorkoutTemplate(id = null, userId = currentUserId(), category = request.category, name = request.name, items = items)
        )
        return TemplateResponse.from(saved)
    }

    // 내 템플릿 목록 조회 (소프트 삭제된 템플릿 제외)
    @GetMapping
    fun list(): List<TemplateResponse> =
        templateRepository.findAllByUserId(currentUserId()).map { TemplateResponse.from(it) }

    // 템플릿 단건 조회. 없거나 다른 유저 소유면 404 (존재 노출 방지)
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<TemplateResponse> {
        val template = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(TemplateResponse.from(template))
    }

    // 템플릿 전체 교체 (items 포함). 없거나 다른 유저 소유면 404
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: TemplateCreateRequest): ResponseEntity<TemplateResponse> {
        val existing = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        val items = request.items.map { it.toDomainItem() }
        val saved = templateRepository.save(existing.copy(category = request.category, name = request.name, items = items))
        return ResponseEntity.ok(TemplateResponse.from(saved))
    }

    // 템플릿 소프트 삭제. 없거나 다른 유저 소유면 404
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        templateRepository.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    // exerciseId로 Exercise를 조회해 타입을 확인하고, 그 타입에 맞는 TemplateItem을 생성
    private fun TemplateItemRequest.toDomainItem(): TemplateItem {
        val exercise = exerciseRepository.findById(exerciseId)
            ?: throw IllegalArgumentException("존재하지 않는 exerciseId: $exerciseId")
        return TemplateItem.create(
            exerciseType = exercise.type, exerciseId = exerciseId, sortOrder = sortOrder,
            targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
            targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
        )
    }

    // id로 조회한 템플릿이 현재 인증 사용자 소유일 때만 반환
    private fun findOwnedOrNull(id: UUID): WorkoutTemplate? {
        val template = templateRepository.findById(id) ?: return null
        return if (template.userId == currentUserId()) template else null
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
