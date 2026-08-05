package com.bali.api.exercise

import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.exercise.ExerciseScope
import com.bali.core.exercise.MuscleGroup
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 운동 종목 카탈로그 관련 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/exercises")
class ExerciseController(
    private val exerciseRepository: ExerciseRepository,
) {

    // 카탈로그 조회 (GLOBAL 전체 + 본인 PERSONAL), muscleGroup으로 선택적 필터링
    @GetMapping
    fun list(@RequestParam(required = false) muscleGroup: MuscleGroup?): List<ExerciseResponse> {
        val visible = exerciseRepository.findVisibleTo(currentUserId())
        val filtered = if (muscleGroup != null) visible.filter { it.muscleGroup == muscleGroup } else visible
        return filtered.map { ExerciseResponse.from(it) }
    }

    // 유사 종목 제안 (trigram 유사도 정렬, 상위 10개 고정)
    @GetMapping("/suggest")
    fun suggest(@RequestParam q: String): List<ExerciseResponse> =
        exerciseRepository.suggest(q, currentUserId(), limit = 10).map { ExerciseResponse.from(it) }

    // 개인 종목 등록 (scope=PERSONAL 자동, ownerId는 인증 컨텍스트에서)
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
            )
        )
        return ExerciseResponse.from(saved)
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
