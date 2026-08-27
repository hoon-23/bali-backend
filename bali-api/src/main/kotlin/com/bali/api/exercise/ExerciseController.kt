package com.bali.api.exercise

import com.bali.api.auth.currentUserId
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.MuscleGroup
import com.bali.core.template.WorkoutTemplateRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 운동 종목 카탈로그 관련 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/exercises")
@Tag(name = "Exercise", description = "운동 종목 카탈로그 API")
class ExerciseController(
    private val exerciseRepository: ExerciseRepository,
    private val templateRepository: WorkoutTemplateRepository,
) {

    // 카탈로그 조회 (GLOBAL 전체 + 본인 PERSONAL), muscleGroup으로 선택적 필터링
    @Operation(summary = "종목 카탈로그 조회", description = "GLOBAL 전체 + 본인 PERSONAL 종목을 조회한다. muscleGroup으로 선택적 필터링 가능")
    @GetMapping
    fun list(@RequestParam(required = false) muscleGroup: MuscleGroup?): List<ExerciseResponse> {
        val visible = exerciseRepository.findVisibleTo(currentUserId())
        val filtered = if (muscleGroup != null) visible.filter { it.muscleGroup == muscleGroup } else visible
        return filtered.map { ExerciseResponse.from(it) }
    }

    // 유사 종목 제안 (trigram 유사도 정렬, 상위 10개 고정)
    @Operation(summary = "유사 종목 제안", description = "trigram 유사도 기준으로 정렬된 상위 10개 종목을 제안한다")
    @GetMapping("/suggest")
    fun suggest(@RequestParam q: String): List<ExerciseResponse> =
        exerciseRepository.suggest(q, currentUserId(), limit = 10).map { ExerciseResponse.from(it) }

    // 개인 종목 등록 (scope=PERSONAL 자동, ownerId는 인증 컨텍스트에서)
    @Operation(summary = "개인 종목 등록", description = "scope=PERSONAL로 자동 등록되며 ownerId는 인증 컨텍스트에서 채워진다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: ExerciseCreateRequest): ExerciseResponse {
        val saved = exerciseRepository.save(
            Exercise(
                id = null,
                name = request.name,
                variant = request.variant,
                muscleGroup = request.muscleGroup,
                type = request.type,
                scope = ExerciseScope.PERSONAL,
                ownerId = currentUserId(),
                equipment = request.equipment,
            )
        )
        return ExerciseResponse.from(saved)
    }

    // 개인 종목 수정 (name/variant/muscleGroup/equipment만, type은 불변). 본인 소유 PERSONAL이 아니면 404
    @Operation(summary = "개인 종목 수정", description = "name/variant/muscleGroup/equipment만 수정 가능(type 불변). 본인 소유 PERSONAL이 아니면 404")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: ExerciseUpdateRequest): ResponseEntity<ExerciseResponse> {
        val existing = findOwnedPersonalOrNull(id) ?: return ResponseEntity.notFound().build()
        val saved = exerciseRepository.save(
            existing.copy(
                name = request.name,
                variant = request.variant,
                muscleGroup = request.muscleGroup,
                equipment = request.equipment,
            )
        )
        return ResponseEntity.ok(ExerciseResponse.from(saved))
    }

    // 개인 종목 삭제. 활성 템플릿이 참조 중이면 409. 본인 소유 PERSONAL이 아니면 404
    @Operation(summary = "개인 종목 삭제", description = "본인 소유 PERSONAL이 아니면 404. 활성 템플릿(소프트 삭제 안 된)이 참조 중이면 409")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        val existing = findOwnedPersonalOrNull(id) ?: return ResponseEntity.notFound().build()
        if (templateRepository.existsActiveReferenceToExercise(existing.id!!)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        exerciseRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    // id로 조회한 종목이 현재 인증 사용자 소유의 PERSONAL 종목일 때만 반환 (GLOBAL/타인 소유는 404 취급)
    private fun findOwnedPersonalOrNull(id: UUID): Exercise? {
        val exercise = exerciseRepository.findById(id) ?: return null
        return if (exercise.scope == ExerciseScope.PERSONAL && exercise.ownerId == currentUserId()) exercise else null
    }
}
