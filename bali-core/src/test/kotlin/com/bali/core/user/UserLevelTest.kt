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
        val gain = XpGain.between(UserLevel.ofTotalXp(200), UserLevel.ofTotalXp(320), hasCompletedLog = true)
        gain.earnedXp shouldBe 120
        gain.baseXp shouldBe 100
        gain.bonusXp shouldBe 20
        gain.zeroReason shouldBe null
    }

    "완료 로그가 있는데 XP가 0이면 하루 한도 도달이다" {
        val gain = XpGain.between(UserLevel.ofTotalXp(200), UserLevel.ofTotalXp(200), hasCompletedLog = true)
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
        XpGain.between(UserLevel.ofTotalXp(0), UserLevel.ofTotalXp(0), hasCompletedLog = false).zeroReason shouldBe XpZeroReason.NO_COMPLETED_LOG
    }
})
