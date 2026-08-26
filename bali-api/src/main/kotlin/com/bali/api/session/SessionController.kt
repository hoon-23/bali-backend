package com.bali.api.session

import com.bali.api.auth.currentUserId
import com.bali.api.template.TemplateItemRequest
import com.bali.core.exercise.Exercise
import com.bali.core.exercise.ExerciseRepository
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.SetTiming
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.template.WorkoutTemplateRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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
@Tag(name = "Session", description = "운동 세션 기록 API")
class SessionController(
    private val sessionRepository: WorkoutSessionRepository,
    private val templateRepository: WorkoutTemplateRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    // 세션 생성. templateId가 있으면 그 템플릿의 items를 target 스냅샷으로 복사, 없으면 빈 세션. status는 date와 무관하게 항상 SCHEDULED로 시작 (이후 전이는 PATCH로 클라이언트가 명시적으로 트리거)
    @Operation(summary = "세션 생성", description = "templateId가 있으면 해당 템플릿의 items를 target 스냅샷으로 복사하고, 없으면 빈 세션으로 생성한다. status는 항상 SCHEDULED로 시작한다")
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
            WorkoutSession(
                id = null, userId = currentUserId(), date = request.date, templateId = request.templateId,
                status = SessionStatus.SCHEDULED, logs = logs,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(saved, resolveTitle(saved)))
    }

    // 세션 목록 커서(무한스크롤) 조회. from/to는 선택이며 있으면 그 범위 내에서, 없으면 전체 이력에서 date DESC로 조회한다
    @Operation(
        summary = "세션 목록 조회 (커서 기반)",
        description = "date DESC로 최대 size개를 반환한다. cursor를 생략하면 최신부터 시작하고, 응답의 nextCursor를 다음 요청에 그대로 넣으면 이어서 조회된다. from/to는 선택 파라미터로 범위를 좁힐 때만 쓴다",
    )
    @GetMapping
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): SessionPageResponse {
        require(size in 1..100) { "size는 1~100 사이여야 합니다" }
        val decodedCursor = cursor?.let { SessionCursor.decode(it) }

        val sessions = sessionRepository.findPageByUserId(
            currentUserId(), from, to,
            cursorDate = decodedCursor?.date, cursorId = decodedCursor?.id,
            limit = size + 1,
        )
        val hasNext = sessions.size > size
        val page = sessions.take(size)

        val exercisesById = exercisesByIdFor(page)
        return SessionPageResponse(
            content = page.map { SessionResponse.from(it, resolveTitle(it, exercisesById)) },
            hasNext = hasNext,
            nextCursor = if (hasNext) page.last().let { SessionCursor(it.date, it.id!!).encode() } else null,
        )
    }

    // 세션 단건 조회. 없거나 다른 유저 소유면 404
    @Operation(summary = "세션 단건 조회", description = "없거나 다른 유저 소유면 404")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<SessionResponse> {
        val session = findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SessionResponse.from(session, resolveTitle(session)))
    }

    // 세션 아이템 구조 변경: addItems(즉흥 추가, target null 허용)/updateItems(logId 기준 전체 교체)/removeLogIds(삭제) + status 전이
    // 언급되지 않은 log의 actual*/completed는 그대로 보존된다. status는 null이면 변경하지 않음 (서버가 자동 추론하지 않고 항상 클라이언트가 명시)
    // @Transactional: 리스트/상태 처리 중 하나라도 실패(require 예외)하면 전체가 롤백되어야 함 (부분 커밋 방지)
    @Operation(
        summary = "세션 아이템 구조 변경 + status 전이",
        description = "addItems(즉흥 추가)/updateItems(logId 기준 전체 교체)/removeLogIds(삭제)/status(전이)를 처리한다. 언급되지 않은 log의 actual*/completed는 보존된다",
    )
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

        request.status?.let { sessionRepository.updateStatus(id, it) }

        request.perceivedDifficulty?.let {
            WorkoutSession.validatePerceivedDifficulty(it)
            sessionRepository.updatePerceivedDifficulty(id, it)
        }

        val updated = sessionRepository.findById(id)!!
        return ResponseEntity.ok(SessionResponse.from(updated, resolveTitle(updated)))
    }

    // 실제 수행값 기록 + 완료 체크. 종목 타입에 맞는 actual 필드 조합인지 검증 후 반영
    @Operation(summary = "세션 로그 실제 수행값 기록", description = "완료 체크와 함께 실제 수행값을 기록한다. 종목 타입에 맞는 actual 필드 조합인지 검증 후 반영")
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
        val setTimings = request.setTimings?.map { SetTiming(it.setIndex, it.startedAt, it.endedAt) }
        SessionLog.validateSetTimings(exerciseType = exercise.type, setTimings = setTimings)

        val updated = sessionRepository.recordActual(
            logId = logId, completed = request.completed,
            actualSets = request.actualSets, actualReps = request.actualReps, actualWeight = request.actualWeight,
            actualDurationSeconds = request.actualDurationSeconds, actualPace = request.actualPace,
            setTimings = setTimings,
        )!!
        return ResponseEntity.ok(SessionLogResponse.from(updated))
    }

    // 세션 삭제. 없거나 다른 유저 소유면 404. session_logs는 DB cascade로 함께 제거됨
    @Operation(summary = "세션 삭제", description = "없거나 다른 유저 소유면 404. session_logs는 DB cascade로 함께 제거된다")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        findOwnedOrNull(id) ?: return ResponseEntity.notFound().build()
        sessionRepository.deleteById(id)
        return ResponseEntity.noContent().build()
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

    // 세션 카드에 바로 쓸 수 있는 title을 계산. templateId가 있고 그 템플릿이 살아있으면 템플릿 이름,
    // 없거나 소프트 삭제되어 조회에 실패하면 종목 이름을 조합("벤치프레스 외 3개")한다
    private fun resolveTitle(session: WorkoutSession, exercisesById: Map<UUID, Exercise> = exercisesByIdFor(listOf(session))): String {
        session.templateId?.let { templateId -> templateRepository.findById(templateId)?.let { return it.name } }
        if (session.logs.isEmpty()) return "빈 세션"
        val first = session.logs.minBy { it.sortOrder }
        val firstName = exercisesById[first.exerciseId]?.name ?: "알 수 없는 종목"
        return if (session.logs.size == 1) firstName else "$firstName 외 ${session.logs.size - 1}개"
    }

    // 여러 세션의 logs에 등장하는 exerciseId를 모아 한 번에 조회 (목록 조회 시 N+1 완화)
    private fun exercisesByIdFor(sessions: List<WorkoutSession>): Map<UUID, Exercise> =
        sessions.flatMap { it.logs }.map { it.exerciseId }.distinct()
            .mapNotNull { exerciseRepository.findById(it)?.let { exercise -> it to exercise } }
            .toMap()

    // id로 조회한 세션이 현재 인증 사용자 소유일 때만 반환
    private fun findOwnedOrNull(id: UUID): WorkoutSession? {
        val session = sessionRepository.findById(id) ?: return null
        return if (session.userId == currentUserId()) session else null
    }
}
