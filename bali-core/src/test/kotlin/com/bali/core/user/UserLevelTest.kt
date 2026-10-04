package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

class UserLevelTest : StringSpec({

    val day = LocalDate.of(2026, 9, 1)

    "기록이 없으면 레벨 1, 0 XP에서 시작한다" {
        UserLevel.fromSessionCounts(emptyMap()) shouldBe UserLevel(level = 1, currentXp = 0, xpForNextLevel = 1000, totalXp = 0)
    }

    "세션 1회는 100 XP다" {
        UserLevel.fromSessionCounts(mapOf(day to 1)).totalXp shouldBe 100
    }

    "하루에 3세션을 해도 2세션까지만 인정한다" {
        UserLevel.fromSessionCounts(mapOf(day to 3)).totalXp shouldBe 200
    }

    "연속 3번째 운동일부터 1.2배가 적용된다" {
        val counts = mapOf(day to 1, day.plusDays(1) to 1, day.plusDays(2) to 1, day.plusDays(3) to 1)
        UserLevel.fromSessionCounts(counts).totalXp shouldBe 100 + 100 + 120 + 120
    }

    "월수금처럼 간격이 3일 이내면 주말을 껴도 연속이 유지된다" {
        // 9/7(월) 9/9(수) 9/11(금) 9/14(월)
        val monday = LocalDate.of(2026, 9, 7)
        val counts = mapOf(monday to 1, monday.plusDays(2) to 1, monday.plusDays(4) to 1, monday.plusDays(7) to 1)
        UserLevel.fromSessionCounts(counts).totalXp shouldBe 100 + 100 + 120 + 120
    }

    "4일 이상 간격이 벌어지면 연속이 끊겨 다시 1배부터 시작한다" {
        val counts = mapOf(day to 1, day.plusDays(1) to 1, day.plusDays(2) to 1, day.plusDays(6) to 1)
        UserLevel.fromSessionCounts(counts).totalXp shouldBe 100 + 100 + 120 + 100
    }

    "연속 보너스가 적용된 날의 2세션은 둘 다 1.2배다" {
        val counts = mapOf(day to 1, day.plusDays(1) to 1, day.plusDays(2) to 2)
        UserLevel.fromSessionCounts(counts).totalXp shouldBe 100 + 100 + 240
    }

    "레벨별 필요 XP는 1000에서 시작해 5퍼센트씩 늘어난다" {
        UserLevel.xpRequiredFor(1) shouldBe 1000
        UserLevel.xpRequiredFor(2) shouldBe 1050
        UserLevel.xpRequiredFor(3) shouldBe 1103
    }

    "999 XP는 레벨 1이고 1000 XP에서 레벨 2가 된다" {
        UserLevel.ofTotalXp(999) shouldBe UserLevel(level = 1, currentXp = 999, xpForNextLevel = 1000, totalXp = 999)
        UserLevel.ofTotalXp(1000) shouldBe UserLevel(level = 2, currentXp = 0, xpForNextLevel = 1050, totalXp = 1000)
    }

    "여러 레벨을 넘는 XP는 레벨별 필요량을 차례로 차감한다" {
        // 1000 + 1050 = 2050에서 레벨 3, 남은 150
        UserLevel.ofTotalXp(2200) shouldBe UserLevel(level = 3, currentXp = 150, xpForNextLevel = 1103, totalXp = 2200)
    }

    "XP 내역은 전후 누적 차이를 기본 100과 보너스로 나눈다" {
        val gain = XpGain.between(UserLevel.ofTotalXp(200), UserLevel.ofTotalXp(320), XpTier.FULL)
        gain.earnedXp shouldBe 120
        gain.baseXp shouldBe 100
        gain.bonusXp shouldBe 20
        gain.zeroReason shouldBe null
    }

    "완료 로그가 있는데 XP가 0이면 하루 한도 도달이다" {
        val gain = XpGain.between(UserLevel.ofTotalXp(200), UserLevel.ofTotalXp(200), XpTier.FULL)
        gain.earnedXp shouldBe 0
        gain.baseXp shouldBe 0
        gain.zeroReason shouldBe XpZeroReason.DAILY_LIMIT
    }

    // 인정 조건 테스트용 세션: 로그 1개(완료 여부, 세트, 횟수, 시간 지정)
    fun session(date: LocalDate, completed: Boolean, sets: Int? = null, reps: Int? = null, durationSeconds: Int? = null) = WorkoutSession(
        id = null, userId = UUID.randomUUID(), date = date, templateId = null, status = SessionStatus.COMPLETED,
        logs = listOf(
            SessionLog(
                id = UUID.randomUUID(), exerciseId = UUID.randomUUID(), sortOrder = 0, completed = completed,
                targetSets = null, targetReps = null, targetWeight = null, targetDurationSeconds = null, targetPace = null,
                actualSets = sets, actualReps = reps, actualWeight = null, actualDurationSeconds = durationSeconds, actualPace = null,
            )
        ),
    )
    val before = UserLevel.STRICT_QUALIFICATION_FROM.minusDays(1)
    val after = UserLevel.STRICT_QUALIFICATION_FROM

    "기준일 이전 세션은 완료 체크만 있어도 인정한다(소급 안 함)" {
        session(before, completed = true).isXpQualified() shouldBe true
    }

    "기준일 이후 세션은 완료 체크만 있고 세트 횟수가 없으면 인정하지 않는다" {
        session(after, completed = true).isXpQualified() shouldBe false
        session(after, completed = true, sets = 0, reps = 0).isXpQualified() shouldBe false
        session(after, completed = true, sets = 3, reps = 0).isXpQualified() shouldBe false
    }

    "기준일 이후 세션도 세트와 횟수가 있으면 인정하고 유산소는 시간이 있으면 인정한다" {
        session(after, completed = true, sets = 3, reps = 10).isXpQualified() shouldBe true
        session(after, completed = true, durationSeconds = 600).isXpQualified() shouldBe true
    }

    "완료 체크가 없으면 기록이 있어도 인정하지 않는다" {
        session(before, completed = false, sets = 3, reps = 10).isXpQualified() shouldBe false
        session(after, completed = false, sets = 3, reps = 10).isXpQualified() shouldBe false
    }

    "완료 로그가 없어 XP가 0이면 NO_COMPLETED_LOG다" {
        XpGain.between(UserLevel.ofTotalXp(0), UserLevel.ofTotalXp(0), XpTier.NONE).zeroReason shouldBe XpZeroReason.NO_COMPLETED_LOG
    }

    // 부분 수행 테스트용 날짜 헬퍼
    val d1 = UserLevel.PARTIAL_QUALIFICATION_FROM
    fun day(offset: Long) = d1.plusDays(offset)

    "부분 수행은 세션당 50 XP이고 연속 보너스가 없다" {
        UserLevel.fromSessionCounts(emptyMap(), mapOf(day(0) to 1, day(1) to 1, day(2) to 1, day(3) to 1)).totalXp shouldBe 200
    }

    "하루 2슬롯은 정상과 부분을 합쳐 XP가 큰 것부터 채운다" {
        UserLevel.fromSessionCounts(mapOf(day(0) to 1), mapOf(day(0) to 1)).totalXp shouldBe 150
        UserLevel.fromSessionCounts(mapOf(day(0) to 2), mapOf(day(0) to 1)).totalXp shouldBe 200
        UserLevel.fromSessionCounts(mapOf(day(0) to 1), mapOf(day(0) to 5)).totalXp shouldBe 150
        UserLevel.fromSessionCounts(emptyMap(), mapOf(day(0) to 3)).totalXp shouldBe 100
    }

    "부분 수행만 있는 날은 연속에 포함하지 않아 정상 운동일 사이를 잇지 못한다" {
        // 정상 day0, 부분 day2, 정상 day4: 정상끼리 간격이 4일이라 연속이 끊긴다(보너스 없음) -> 100 + 50 + 100
        UserLevel.fromSessionCounts(mapOf(day(0) to 1, day(4) to 1), mapOf(day(2) to 1)).totalXp shouldBe 250
        // 정상 day0,1,3: 3번째 운동일(day3)부터 120. 사이의 부분 수행(day2)은 연속 계산에 영향이 없다
        UserLevel.fromSessionCounts(mapOf(day(0) to 1, day(1) to 1, day(3) to 1), mapOf(day(2) to 1)).totalXp shouldBe 100 + 100 + 50 + 120
    }

    "정상 세션이 부분 수행을 밀어내면 XP 차이만 얻고 baseXp는 그 차이를 넘지 않는다" {
        val before = UserLevel.fromSessionCounts(mapOf(day(0) to 1), mapOf(day(0) to 1))
        val after = UserLevel.fromSessionCounts(mapOf(day(0) to 2), mapOf(day(0) to 1))
        val gain = XpGain.between(before, after, XpTier.FULL)
        gain.earnedXp shouldBe 50
        gain.baseXp shouldBe 50
        gain.bonusXp shouldBe 0
    }

    "부분 수행 XP 내역은 baseXp 50이고 partial이 true다" {
        val gain = XpGain.between(UserLevel.ofTotalXp(0), UserLevel.ofTotalXp(50), XpTier.PARTIAL)
        gain.earnedXp shouldBe 50
        gain.baseXp shouldBe 50
        gain.bonusXp shouldBe 0
        gain.partial shouldBe true
        gain.zeroReason shouldBe null
    }

    "부분 수행인데 하루 한도로 XP가 0이면 DAILY_LIMIT이고 partial은 true다" {
        val gain = XpGain.between(UserLevel.ofTotalXp(200), UserLevel.ofTotalXp(200), XpTier.PARTIAL)
        gain.zeroReason shouldBe XpZeroReason.DAILY_LIMIT
        gain.partial shouldBe true
    }

    "정상 완료 내역은 partial이 false다" {
        XpGain.between(UserLevel.ofTotalXp(0), UserLevel.ofTotalXp(100), XpTier.FULL).partial shouldBe false
    }

    "세션 등급: 완료 인정은 FULL, 시작일 이후 기록 기준을 채우면 PARTIAL, 그 외 NONE" {
        val start = UserLevel.PARTIAL_QUALIFICATION_FROM
        session(start, completed = true, sets = 3, reps = 10).xpTier() shouldBe XpTier.FULL
        session(start, completed = false, sets = 2, reps = 10).xpTier() shouldBe XpTier.PARTIAL
        session(start, completed = false, sets = 1, reps = 10).xpTier() shouldBe XpTier.NONE
        session(start, completed = false, sets = 5, reps = 0).xpTier() shouldBe XpTier.NONE
        session(start, completed = false, durationSeconds = 1200).xpTier() shouldBe XpTier.PARTIAL
        session(start, completed = false, durationSeconds = 1199).xpTier() shouldBe XpTier.NONE
        session(start.minusDays(1), completed = false, sets = 5, reps = 10).xpTier() shouldBe XpTier.NONE
    }

    "여러 로그의 세트는 합산하고 reps가 없는 로그의 세트는 제외한다" {
        val base = session(UserLevel.PARTIAL_QUALIFICATION_FROM, completed = false, sets = 1, reps = 8)
        // 1세트 + 1세트 = 2세트로 부분 수행 인정
        val two = base.copy(logs = base.logs + base.logs.first().copy(id = UUID.randomUUID(), sortOrder = 1))
        two.xpTier() shouldBe XpTier.PARTIAL
        // 두 번째 로그의 reps가 0이면 그 세트는 합산하지 않는다
        val noReps = base.copy(logs = base.logs + base.logs.first().copy(id = UUID.randomUUID(), sortOrder = 1, actualReps = 0))
        noReps.xpTier() shouldBe XpTier.NONE
    }
})
