package com.bali.infra.session

import com.bali.core.exercise.ExerciseType
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.SetTiming
import com.bali.core.session.WorkoutSession
import com.bali.infra.InfraTestConfig
import com.bali.infra.exercise.ExerciseJpaEntity
import com.bali.infra.exercise.ExerciseJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [InfraTestConfig::class])
@Import(WorkoutSessionRepositoryAdapter::class)
class WorkoutSessionRepositoryAdapterTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/bali" }
            registry.add("spring.datasource.username") { "bali" }
            registry.add("spring.datasource.password") { "bali" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    // 기준일을 먼 미래로 두면 모든 세션이 "기준일 이전"이 되어 완료 체크만으로 집계된다 (기존 동작 검증용)
    private val NO_STRICT = LocalDate.of(9999, 1, 1)

    @Autowired
    lateinit var adapter: WorkoutSessionRepositoryAdapter

    @Autowired
    lateinit var exerciseJpaRepository: ExerciseJpaRepository

    // session_logs.exercise_id에 FK가 걸려 있어(V23) 실제 존재하는 exercise를 참조해야 한다
    private fun persistExercise(): UUID = exerciseJpaRepository.save(ExerciseJpaEntity()).id

    private fun log(exerciseId: UUID = persistExercise()) = SessionLog.create(
        exerciseType = ExerciseType.STRENGTH, exerciseId = exerciseId, sortOrder = 0,
        targetSets = 3, targetReps = 10, targetWeight = BigDecimal("60.0"),
    )

    private fun completedLog(exerciseId: UUID = persistExercise()) = log(exerciseId).copy(
        completed = true, actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"),
    )

    @Test
    fun `save then findById returns the session with its logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        val found = adapter.findById(saved.id!!)

        assertEquals(1, found?.logs?.size)
    }

    @Test
    fun `findAllByUserId only returns sessions within the date range, each with its own logs`() {
        val userId = UUID.randomUUID()
        val firstExerciseId = persistExercise()
        val secondExerciseId = persistExercise()
        val outOfRangeExerciseId = persistExercise()
        val first = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(firstExerciseId))))
        val second = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 10), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(secondExerciseId))))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(outOfRangeExerciseId))))

        val inRange = adapter.findAllByUserId(userId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        assertEquals(2, inRange.size)
        val firstResult = inRange.single { it.id == first.id }
        val secondResult = inRange.single { it.id == second.id }
        assertEquals(listOf(firstExerciseId), firstResult.logs.map { it.exerciseId })
        assertEquals(listOf(secondExerciseId), secondResult.logs.map { it.exerciseId })
    }

    @Test
    fun `findPageByUserId는 date DESC 순으로 limit개만 반환하고, 같은 date는 id DESC로 tie-break한다`() {
        val userId = UUID.randomUUID()
        val oldest = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val sameDateA = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList(), createdAt = Instant.parse("2026-08-03T09:00:00Z")))
        val sameDateB = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList(), createdAt = Instant.parse("2026-08-03T10:00:00Z")))
        val expectedOrder = listOf(sameDateB.id, sameDateA.id) // 같은 date는 나중에 만든 세션이 먼저

        val page = adapter.findPageByUserId(userId, from = null, to = null, cursorDate = null, cursorSortTime = null, cursorId = null, limit = 2)

        assertEquals(expectedOrder, page.map { it.id })
        assertTrue(page.none { it.id == oldest.id })
    }

    @Test
    fun `findPageByUserId는 cursor 이전 세션만 반환해 이어서 조회할 수 있다`() {
        val userId = UUID.randomUUID()
        val first = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val second = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 2), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        val third = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 3), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        // 커서를 third 자신의 (date, id)로 지정: id < 자기 자신은 항상 false이므로 third는 결정적으로 제외된다
        val nextPage = adapter.findPageByUserId(userId, from = null, to = null, cursorDate = third.date, cursorSortTime = third.completedAt ?: third.createdAt, cursorId = third.id!!, limit = 10)

        assertEquals(setOf(first.id, second.id), nextPage.map { it.id }.toSet())
    }

    @Test
    fun `같은 date에서는 완료 시각이 늦은 세션이 먼저 나오고 완료 전 세션은 생성 시각으로 비교한다`() {
        val userId = UUID.randomUUID()
        val date = LocalDate.of(2026, 11, 18)
        fun save(created: String, completed: String?) = adapter.save(WorkoutSession(
            id = null, userId = userId, date = date, templateId = null,
            status = if (completed != null) SessionStatus.COMPLETED else SessionStatus.SCHEDULED, logs = emptyList(),
            createdAt = Instant.parse(created), completedAt = completed?.let { Instant.parse(it) },
        ))
        val earlyDone = save("2026-11-18T01:00:00Z", "2026-11-18T02:00:00Z")
        val lateDone = save("2026-11-18T00:30:00Z", "2026-11-18T05:00:00Z")
        val scheduled = save("2026-11-18T03:00:00Z", null)

        val page = adapter.findPageByUserId(userId, null, null, null, null, null, limit = 10)

        assertEquals(listOf(lateDone.id, scheduled.id, earlyDone.id), page.map { it.id })
    }

    @Test
    fun `구버전 커서(sortTime 없음)는 가리키는 세션의 정렬 시각으로 보완해 이어서 조회한다`() {
        val userId = UUID.randomUUID()
        val date = LocalDate.of(2026, 11, 18)
        val older = adapter.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList(), createdAt = Instant.parse("2026-11-18T01:00:00Z")))
        val newer = adapter.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList(), createdAt = Instant.parse("2026-11-18T02:00:00Z")))

        val page = adapter.findPageByUserId(userId, null, null, date, null, newer.id!!, limit = 10)

        assertEquals(listOf(older.id), page.map { it.id })
    }

    @Test
    fun `updateStatus는 COMPLETED로 전이할 때만 completedAt을 기록한다`() {
        val saved = adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = LocalDate.of(2026, 11, 18), templateId = null, status = SessionStatus.IN_PROGRESS, logs = emptyList()))
        assertNull(saved.completedAt)

        val completed = adapter.updateStatus(saved.id!!, SessionStatus.COMPLETED)!!
        assertNotNull(completed.completedAt)

        val firstCompletedAt = completed.completedAt
        assertEquals(firstCompletedAt, adapter.updateStatus(saved.id!!, SessionStatus.COMPLETED)!!.completedAt)
    }

    @Test
    fun `findPageByUserId는 from to 범위와 함께 쓰면 그 범위 내에서만 조회한다`() {
        val userId = UUID.randomUUID()
        val inRange = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val page = adapter.findPageByUserId(userId, from = LocalDate.of(2026, 8, 1), to = LocalDate.of(2026, 8, 31), cursorDate = null, cursorSortTime = null, cursorId = null, limit = 10)

        assertEquals(listOf(inRange.id), page.map { it.id })
    }

    @Test
    fun `addLogs appends a new log without touching existing ones`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        adapter.addLogs(saved.id!!, listOf(log()))

        assertEquals(2, adapter.findById(saved.id!!)?.logs?.size)
    }

    @Test
    fun `removeLogs deletes only the specified logs`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(), log())))
        val logIdToRemove = saved.logs[0].id!!

        adapter.removeLogs(saved.id!!, listOf(logIdToRemove))

        val remaining = adapter.findById(saved.id!!)?.logs
        assertEquals(1, remaining?.size)
        assertTrue(remaining?.none { it.id == logIdToRemove } ?: false)
    }

    @Test
    fun `recordActual sets actual values and completed, leaving unspecified fields unchanged`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!

        // first call establishes a non-null actualDurationSeconds baseline
        adapter.recordActual(
            logId = logId, completed = false,
            actualSets = null, actualReps = null, actualWeight = null,
            actualDurationSeconds = 120, actualPace = null,
            setTimings = null,
        )

        // second call passes actualDurationSeconds = null; if the implementation clobbers
        // instead of skipping null params, the previously-set value of 120 would be lost
        val updated = adapter.recordActual(
            logId = logId, completed = true,
            actualSets = 3, actualReps = 10, actualWeight = BigDecimal("60.0"),
            actualDurationSeconds = null, actualPace = null,
            setTimings = null,
        )

        assertEquals(true, updated?.completed)
        assertEquals(3, updated?.actualSets)
        assertEquals(120, updated?.actualDurationSeconds)
    }

    @Test
    fun `recordActual로 setTimings를 저장하면 JSON round-trip으로 Instant까지 온전히 조회된다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!
        val timings = listOf(
            SetTiming(0, Instant.parse("2026-08-18T10:00:00Z"), Instant.parse("2026-08-18T10:00:45Z")),
            SetTiming(1, Instant.parse("2026-08-18T10:02:10Z"), Instant.parse("2026-08-18T10:02:58Z")),
        )

        adapter.recordActual(
            logId = logId, completed = true,
            actualSets = null, actualReps = null, actualWeight = null,
            actualDurationSeconds = null, actualPace = null,
            setTimings = timings,
        )

        val found = adapter.findById(saved.id!!)
        assertEquals(timings, found?.logs?.get(0)?.setTimings)
    }

    @Test
    fun `updateStatus로 status만 갱신되고 존재하지 않는 세션이면 null을 반환한다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))

        val updated = adapter.updateStatus(saved.id!!, SessionStatus.IN_PROGRESS)

        assertEquals(SessionStatus.IN_PROGRESS, updated?.status)
        assertEquals(SessionStatus.IN_PROGRESS, adapter.findById(saved.id!!)?.status)
        assertEquals(null, adapter.updateStatus(UUID.randomUUID(), SessionStatus.COMPLETED))
    }

    @Test
    fun `deleteById로 세션을 삭제하면 소속 logs도 DB cascade로 함께 제거된다`() {
        val userId = UUID.randomUUID()
        val saved = adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 6), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        val logId = saved.logs[0].id!!

        adapter.deleteById(saved.id!!)

        assertEquals(null, adapter.findById(saved.id!!))
        assertEquals(null, adapter.findLogById(logId))
    }

    @Test
    fun `findActiveDates는 completed 로그가 있는 날짜만, since 이후만 반환한다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 8, 14), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log())))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.of(2026, 1, 1), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))
        // 다른 사용자의 same-range completed 세션이 결과에 섞여 들어오지 않는지 검증 (userId 필터 미검증 방지)
        adapter.save(WorkoutSession(id = null, userId = otherUserId, date = LocalDate.of(2026, 8, 15), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(completedLog())))

        val activeDates = adapter.findActiveDates(userId, since = LocalDate.of(2026, 8, 1), strictFrom = NO_STRICT)

        assertEquals(setOf(LocalDate.of(2026, 8, 15)), activeDates)
    }

    // 경험치 인정 집계: 기준일 이전은 완료 체크만으로, 이후는 세트·횟수(또는 시간) 기록이 있어야 인정. COMPLETED만 센다
    @Test
    fun `countQualifiedSessionsByDate는 기준일 이후 세션에만 수행 기록을 요구한다`() {
        val userId = UUID.randomUUID()
        val strictFrom = LocalDate.of(2026, 10, 2)
        fun saveSession(date: LocalDate, status: SessionStatus, log: SessionLog) =
            adapter.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = status, logs = listOf(log)))
        val checkedOnly = log().copy(completed = true)
        val performed = completedLog()
        val cardio = log().copy(completed = true, actualDurationSeconds = 600)

        saveSession(LocalDate.of(2026, 9, 30), SessionStatus.COMPLETED, checkedOnly)
        saveSession(LocalDate.of(2026, 10, 1), SessionStatus.ABANDONED, performed)
        saveSession(LocalDate.of(2026, 10, 2), SessionStatus.COMPLETED, checkedOnly)
        saveSession(LocalDate.of(2026, 10, 3), SessionStatus.COMPLETED, performed)
        saveSession(LocalDate.of(2026, 10, 3), SessionStatus.COMPLETED, cardio)
        saveSession(LocalDate.of(2026, 10, 4), SessionStatus.COMPLETED, log())

        val counts = adapter.countQualifiedSessionsByDate(userId, strictFrom)

        assertEquals(mapOf(LocalDate.of(2026, 9, 30) to 1, LocalDate.of(2026, 10, 3) to 2), counts)
    }

    // 부분 수행 집계: 완료 인정 로그가 없고 기록된 세트(reps>0) 합 또는 유산소 시간 합이 기준 이상인 COMPLETED 세션만, from 이후 날짜만 센다
    @Test
    fun `countPartialSessionsByDate는 인정 로그 없이 수행량만 기준 이상인 COMPLETED 세션을 센다`() {
        val userId = UUID.randomUUID()
        val from = LocalDate.of(2026, 10, 3)
        fun saveSession(date: LocalDate, status: SessionStatus, vararg logs: SessionLog) =
            adapter.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = status, logs = logs.toList()))
        val twoSetsUnchecked = log().copy(actualSets = 2, actualReps = 10)
        val oneSetUnchecked = log().copy(actualSets = 1, actualReps = 10)
        val cardio20min = log().copy(actualDurationSeconds = 1200)
        val cardio19min = log().copy(actualDurationSeconds = 1199)

        saveSession(LocalDate.of(2026, 10, 3), SessionStatus.COMPLETED, twoSetsUnchecked)
        saveSession(LocalDate.of(2026, 10, 3), SessionStatus.COMPLETED, oneSetUnchecked, oneSetUnchecked.copy(sortOrder = 1))
        saveSession(LocalDate.of(2026, 10, 4), SessionStatus.COMPLETED, cardio20min)
        saveSession(LocalDate.of(2026, 10, 4), SessionStatus.COMPLETED, oneSetUnchecked)
        saveSession(LocalDate.of(2026, 10, 4), SessionStatus.COMPLETED, cardio19min)
        // 인정 로그(완료+수행 기록)가 있으면 정상 세션이라 부분 수행에서 제외
        saveSession(LocalDate.of(2026, 10, 5), SessionStatus.COMPLETED, completedLog(), twoSetsUnchecked)
        // COMPLETED가 아니거나 시작일 이전이면 제외
        saveSession(LocalDate.of(2026, 10, 6), SessionStatus.ABANDONED, twoSetsUnchecked)
        saveSession(LocalDate.of(2026, 10, 2), SessionStatus.COMPLETED, twoSetsUnchecked)
        // reps가 없는 세트는 합산하지 않는다
        saveSession(LocalDate.of(2026, 10, 7), SessionStatus.COMPLETED, log().copy(actualSets = 3, actualReps = 0))

        val counts = adapter.countPartialSessionsByDate(userId, from, minSets = 2, minCardioSeconds = 1200)

        assertEquals(mapOf(LocalDate.of(2026, 10, 3) to 2, LocalDate.of(2026, 10, 4) to 1), counts)
    }

    @Test
    fun `findAllByDateAndStatus는 유저 무관하게 날짜+상태가 일치하는 세션을 반환한다`() {
        val date = LocalDate.now()
        val matching = adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.COMPLETED, logs = emptyList()))
        adapter.save(WorkoutSession(id = null, userId = UUID.randomUUID(), date = date.minusDays(1), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val result = adapter.findAllByDateAndStatus(date, SessionStatus.SCHEDULED)

        assertTrue(result.any { it.id == matching.id })
        assertTrue(result.all { it.date == date && it.status == SessionStatus.SCHEDULED })
    }

    @Test
    fun `findLastActiveDate는 완료된 로그가 있는 가장 최근 날짜를 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = persistExercise()
        val oldLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        val recentLog = SessionLog.create(ExerciseType.STRENGTH, exerciseId, sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(10), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(oldLog)))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(2), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(recentLog)))

        assertEquals(LocalDate.now().minusDays(2), adapter.findLastActiveDate(userId, NO_STRICT))
    }

    // 기준일 이후 날짜는 세트·횟수(또는 시간) 기록이 있는 완료 로그만 운동한 날로 본다. 기준일 이전은 완료 체크만으로 인정
    @Test
    fun `findActiveDates와 findLastActiveDate는 기준일 이후 날짜에 수행 기록을 요구한다`() {
        val userId = UUID.randomUUID()
        val strictFrom = LocalDate.of(2026, 10, 2)
        fun saveSession(date: LocalDate, log: SessionLog) =
            adapter.save(WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.COMPLETED, logs = listOf(log)))
        saveSession(LocalDate.of(2026, 9, 30), log().copy(completed = true))
        saveSession(LocalDate.of(2026, 10, 3), completedLog())
        saveSession(LocalDate.of(2026, 10, 4), log().copy(completed = true, actualDurationSeconds = 600))
        saveSession(LocalDate.of(2026, 10, 5), log().copy(completed = true))
        saveSession(LocalDate.of(2026, 10, 6), log().copy(completed = true, actualSets = 0, actualReps = 0))

        assertEquals(
            setOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)),
            adapter.findActiveDates(userId, since = LocalDate.of(2026, 9, 1), strictFrom = strictFrom),
        )
        assertEquals(LocalDate.of(2026, 10, 4), adapter.findLastActiveDate(userId, strictFrom))
    }

    @Test
    fun `완료된 로그가 없으면 findLastActiveDate는 null을 반환한다`() {
        assertEquals(null, adapter.findLastActiveDate(UUID.randomUUID(), NO_STRICT))
    }

    @Test
    fun `findLastActualWeightsByExerciseIds는 종목별 가장 최근 세션의 actualWeight를 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = persistExercise()
        val oldLog = completedLog(exerciseId).copy(actualWeight = BigDecimal("40.0"))
        val recentLog = completedLog(exerciseId).copy(actualWeight = BigDecimal("50.0"))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(10), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(oldLog)))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(2), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(recentLog)))

        val result = adapter.findLastActualWeightsByExerciseIds(userId, listOf(exerciseId))

        assertEquals(BigDecimal("50.0"), result[exerciseId])
    }

    @Test
    fun `findLastActualWeightsByExerciseIds는 actualWeight가 없는 로그와 다른 유저 기록은 제외한다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val exerciseId = persistExercise()
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = listOf(log(exerciseId))))
        adapter.save(WorkoutSession(id = null, userId = otherUserId, date = LocalDate.now(), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(completedLog(exerciseId))))

        val result = adapter.findLastActualWeightsByExerciseIds(userId, listOf(exerciseId))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findLastActualWeightsByExerciseIds는 exerciseIds가 비어있으면 빈 맵을 반환한다`() {
        assertEquals(emptyMap<UUID, BigDecimal>(), adapter.findLastActualWeightsByExerciseIds(UUID.randomUUID(), emptyList()))
    }

    // 기준 날짜 이전의 IN_PROGRESS만 ABANDONED로 바뀌고, 다른 상태/오늘 날짜/로그는 그대로인지 확인
    @Test
    fun `abandonInProgressBefore는 기준 날짜 이전의 IN_PROGRESS만 ABANDONED로 바꾸고 로그를 보존한다`() {
        val userId = UUID.randomUUID()
        val today = LocalDate.of(2026, 10, 1)
        val stale = adapter.save(WorkoutSession(id = null, userId = userId, date = today.minusDays(1), templateId = null, status = SessionStatus.IN_PROGRESS, logs = listOf(completedLog())))
        val todays = adapter.save(WorkoutSession(id = null, userId = userId, date = today, templateId = null, status = SessionStatus.IN_PROGRESS, logs = emptyList()))
        val oldCompleted = adapter.save(WorkoutSession(id = null, userId = userId, date = today.minusDays(5), templateId = null, status = SessionStatus.COMPLETED, logs = emptyList()))
        val oldScheduled = adapter.save(WorkoutSession(id = null, userId = userId, date = today.minusDays(2), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val updated = adapter.abandonInProgressBefore(today)

        assertTrue(updated >= 1)
        assertEquals(SessionStatus.ABANDONED, adapter.findById(stale.id!!)?.status)
        assertEquals(SessionStatus.IN_PROGRESS, adapter.findById(todays.id!!)?.status)
        assertEquals(SessionStatus.COMPLETED, adapter.findById(oldCompleted.id!!)?.status)
        assertEquals(SessionStatus.SCHEDULED, adapter.findById(oldScheduled.id!!)?.status)
        val preserved = adapter.findById(stale.id!!)!!.logs.single()
        assertTrue(preserved.completed)
        assertEquals(3, preserved.actualSets)
        assertEquals(0, adapter.abandonInProgressBefore(today))
    }

    // 같은 종목을 여러 번 했으면 가장 최근 세션의 수행값이 나오는지 확인
    @Test
    fun `findLastRecordsByExerciseIds는 종목별 가장 최근 세션의 수행 기록을 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = persistExercise()
        val oldLog = completedLog(exerciseId).copy(actualSets = 3, actualReps = 10, actualWeight = BigDecimal("40.0"))
        val recentLog = completedLog(exerciseId).copy(actualSets = 4, actualReps = 8, actualWeight = BigDecimal("50.0"))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(10), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(oldLog)))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(2), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(recentLog)))

        val record = adapter.findLastRecordsByExerciseIds(userId, listOf(exerciseId)).single()

        assertEquals(exerciseId, record.exerciseId)
        assertEquals(4, record.sets)
        assertEquals(8, record.reps)
        assertEquals(BigDecimal("50.0"), record.weight)
        assertEquals(null, record.durationSeconds)
    }

    // 더 최근 세션이라도 미수행(actualSets null/0) 로그는 건너뛰고, 다른 유저 기록은 섞이지 않는지 확인
    @Test
    fun `findLastRecordsByExerciseIds는 미수행 로그와 다른 유저 기록은 제외한다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val performedId = persistExercise()
        val neverPerformedId = persistExercise()
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(5), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(completedLog(performedId))))
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now().minusDays(1), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(log(performedId).copy(actualSets = 0), log(neverPerformedId))))
        adapter.save(WorkoutSession(id = null, userId = otherUserId, date = LocalDate.now(), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(completedLog(neverPerformedId))))

        val result = adapter.findLastRecordsByExerciseIds(userId, listOf(performedId, neverPerformedId))

        assertEquals(listOf(performedId), result.map { it.exerciseId })
        assertEquals(3, result.single().sets)
    }

    // 유산소 로그는 수행 시간만 채워져 나오는지 확인
    @Test
    fun `findLastRecordsByExerciseIds는 유산소 종목이면 수행 시간만 반환한다`() {
        val userId = UUID.randomUUID()
        val exerciseId = persistExercise()
        val cardioLog = SessionLog.create(exerciseType = ExerciseType.CARDIO, exerciseId = exerciseId, sortOrder = 0, targetDurationSeconds = 1200)
            .copy(completed = true, actualDurationSeconds = 1500)
        adapter.save(WorkoutSession(id = null, userId = userId, date = LocalDate.now(), templateId = null, status = SessionStatus.COMPLETED, logs = listOf(cardioLog)))

        val record = adapter.findLastRecordsByExerciseIds(userId, listOf(exerciseId)).single()

        assertEquals(1500, record.durationSeconds)
        assertEquals(null, record.sets)
        assertEquals(null, record.weight)
    }

    @Test
    fun `findLastRecordsByExerciseIds는 exerciseIds가 비어있으면 빈 목록을 반환한다`() {
        assertTrue(adapter.findLastRecordsByExerciseIds(UUID.randomUUID(), emptyList()).isEmpty())
    }
}
