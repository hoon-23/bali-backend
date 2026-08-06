package com.bali.api.session

import com.bali.api.template.TemplateItemRequest
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.session.SessionLog
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.template.WorkoutTemplateRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
                val exercise = exerciseRepository.findVisibleTo(item.exerciseId, currentUserId())
                    ?: throw IllegalArgumentException("존재하지 않는 exerciseId: ${item.exerciseId}")
                SessionLog.create(
                    exerciseType = exercise.type,
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

    // 세션 아이템 구조 변경: addItems(즉흥 추가, target null 허용)/updateItems(logId 기준 전체 교체)/removeLogIds(삭제)
    // 언급되지 않은 log의 actual*/completed는 그대로 보존된다
    // @Transactional: 세 리스트 처리 중 하나라도 실패(require 예외)하면 전체가 롤백되어야 함 (부분 커밋 방지)
    @Transactional
    @PatchMapping("/{id}")
    fun patch(@PathVariable id: UUID, @RequestBody request: SessionPatchRequest): ResponseEntity<SessionResponse> {
        val session = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        val existingLogIds = session.logs.mapNotNull { it.id }.toSet()

        val newLogs = request.addItems.map { it.toSessionLog() }
        if (newLogs.isNotEmpty()) sessionRepository.addLogs(id, newLogs)

        if (request.updateItems.isNotEmpty()) {
            val updatedLogs = request.updateItems.map { update ->
                require(update.logId in existingLogIds) { "세션에 속하지 않는 logId: ${update.logId}" }
                val existing = session.logs.first { it.id == update.logId }
                val exercise = exerciseRepository.findVisibleTo(update.exerciseId, currentUserId())
                    ?: throw IllegalArgumentException("존재하지 않는 exerciseId: ${update.exerciseId}")
                val replacement = SessionLog.create(
                    exerciseType = exercise.type, exerciseId = update.exerciseId, sortOrder = update.sortOrder,
                    targetSets = update.targetSets, targetReps = update.targetReps, targetWeight = update.targetWeight,
                    targetDurationSeconds = update.targetDurationSeconds, targetPace = update.targetPace,
                ).copy(id = update.logId)
                // exerciseId가 그대로면 종목 변경이 아니므로 completed/actual*를 보존한다 (바뀌었으면 이전 수행 기록은 무효)
                if (update.exerciseId == existing.exerciseId) {
                    replacement.copy(
                        completed = existing.completed,
                        actualSets = existing.actualSets,
                        actualReps = existing.actualReps,
                        actualWeight = existing.actualWeight,
                        actualDurationSeconds = existing.actualDurationSeconds,
                        actualPace = existing.actualPace,
                    )
                } else {
                    replacement
                }
            }
            sessionRepository.updateLogs(id, updatedLogs)
        }

        if (request.removeLogIds.isNotEmpty()) {
            request.removeLogIds.forEach { logId ->
                require(logId in existingLogIds) { "세션에 속하지 않는 logId: $logId" }
            }
            sessionRepository.removeLogs(id, request.removeLogIds)
        }

        val updated = sessionRepository.findById(id)!!
        return ResponseEntity.ok(SessionResponse.from(updated))
    }

    // 실제 수행값 기록 + 완료 체크. 종목 타입에 맞는 actual 필드 조합인지 검증 후 반영
    @PatchMapping("/{id}/logs/{logId}")
    fun patchLog(
        @PathVariable id: UUID,
        @PathVariable logId: UUID,
        @RequestBody request: SessionLogPatchRequest,
    ): ResponseEntity<SessionLogResponse> {
        val session = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        val log = session.logs.find { it.id == logId } ?: return ResponseEntity.notFound().build()

        val exercise = exerciseRepository.findVisibleTo(log.exerciseId, currentUserId())
            ?: throw IllegalArgumentException("존재하지 않는 exerciseId: ${log.exerciseId}")
        SessionLog.validateActualFields(
            exerciseType = exercise.type,
            actualSets = request.actualSets, actualReps = request.actualReps, actualWeight = request.actualWeight,
            actualDurationSeconds = request.actualDurationSeconds, actualPace = request.actualPace,
        )

        val updated = sessionRepository.recordActual(
            logId = logId, completed = request.completed,
            actualSets = request.actualSets, actualReps = request.actualReps, actualWeight = request.actualWeight,
            actualDurationSeconds = request.actualDurationSeconds, actualPace = request.actualPace,
        )!!
        return ResponseEntity.ok(SessionLogResponse.from(updated))
    }

    // TemplateItemRequest를 즉흥 추가 SessionLog로 변환 (target null 허용, 본인이 볼 수 없는 종목이면 미존재로 취급)
    private fun TemplateItemRequest.toSessionLog(): SessionLog {
        val exercise = exerciseRepository.findVisibleTo(exerciseId, currentUserId())
            ?: throw IllegalArgumentException("존재하지 않는 exerciseId: $exerciseId")
        return SessionLog.create(
            exerciseType = exercise.type, exerciseId = exerciseId, sortOrder = sortOrder,
            targetSets = targetSets, targetReps = targetReps, targetWeight = targetWeight,
            targetDurationSeconds = targetDurationSeconds, targetPace = targetPace,
        )
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
