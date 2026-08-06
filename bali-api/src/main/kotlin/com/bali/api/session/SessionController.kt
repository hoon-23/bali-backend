package com.bali.api.session

import com.bali.core.exercise.ExerciseRepository
import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.template.WorkoutTemplateRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

// 운동 세션 기록 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val sessionRepository: WorkoutSessionRepository,
    private val templateRepository: WorkoutTemplateRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    // 세션 생성. templateId가 있으면 그 템플릿의 items를 target 스냅샷으로 복사, 없으면 빈 세션
    @PostMapping
    fun create(@RequestBody request: SessionCreateRequest): ResponseEntity<SessionResponse> {
        val logs = if (request.templateId != null) {
            val template = templateRepository.findById(request.templateId)
                ?: return ResponseEntity.notFound().build()
            if (template.userId != currentUserId()) return ResponseEntity.notFound().build()
            template.items.map { item ->
                SessionLog.create(
                    exerciseType = exerciseRepository.findById(item.exerciseId)!!.type,
                    exerciseId = item.exerciseId, sortOrder = item.sortOrder,
                    targetSets = item.targetSets, targetReps = item.targetReps, targetWeight = item.targetWeight,
                    targetDurationSeconds = item.targetDurationSeconds, targetPace = item.targetPace,
                )
            }
        } else {
            emptyList()
        }
        val saved = sessionRepository.save(
            WorkoutSession(id = null, userId = currentUserId(), date = request.date, templateId = request.templateId, logs = logs)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(saved))
    }

    // 기간별(from~to, inclusive) 세션 조회
    @GetMapping
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<SessionResponse> =
        sessionRepository.findAllByUserId(currentUserId(), from, to).map { SessionResponse.from(it) }

    // 세션 단건 조회. 없거나 다른 유저 소유면 404
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<SessionResponse> {
        val session = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SessionResponse.from(session))
    }

    // id로 조회한 세션이 현재 인증 사용자 소유일 때만 반환
    private fun findOwnedOrNull(id: UUID): WorkoutSession? {
        val session = sessionRepository.findById(id) ?: return null
        return if (session.userId == currentUserId()) session else null
    }

    // SecurityContextHolder에서 현재 인증된 사용자의 UUID를 추출
    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.name)
}
