package com.bali.core.analysis

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WeeklyAnalysisTest : StringSpec({

    fun summary() = AnalysisSummary(
        totalWorkoutMinutes = 60,
        volumeByExercise = emptyMap(),
        volumeByMuscleGroup = emptyMap(),
        cardioTotalMinutes = 0,
        completionRate = BigDecimal("100.0"),
        volumeChangeFromLastWeekPercent = null,
    )

    "SUCCESS 상태는 summary가 있으면 정상 생성된다" {
        val analysis = WeeklyAnalysis(
            id = null, userId = UUID.randomUUID(), weekOf = LocalDate.of(2026, 8, 3),
            status = AnalysisStatus.SUCCESS, summary = summary(), insights = emptyList(),
        )
        analysis.status shouldBe AnalysisStatus.SUCCESS
    }

    "SUCCESS 상태에 summary가 없으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            WeeklyAnalysis(
                id = null, userId = UUID.randomUUID(), weekOf = LocalDate.of(2026, 8, 3),
                status = AnalysisStatus.SUCCESS, summary = null, insights = emptyList(),
            )
        }
    }

    "NO_ACTIVITY 상태에 summary가 있으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            WeeklyAnalysis(
                id = null, userId = UUID.randomUUID(), weekOf = LocalDate.of(2026, 8, 3),
                status = AnalysisStatus.NO_ACTIVITY, summary = summary(), insights = emptyList(),
            )
        }
    }

    "FAILED 상태에 insights가 있으면 예외가 발생한다" {
        shouldThrow<IllegalArgumentException> {
            WeeklyAnalysis(
                id = null, userId = UUID.randomUUID(), weekOf = LocalDate.of(2026, 8, 3),
                status = AnalysisStatus.FAILED, summary = null, insights = listOf(Insight(id = null, summaryText = "x")),
            )
        }
    }

    "NO_ACTIVITY 상태는 summary/insights가 비어있으면 정상 생성된다" {
        val analysis = WeeklyAnalysis(
            id = null, userId = UUID.randomUUID(), weekOf = LocalDate.of(2026, 8, 3),
            status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList(),
        )
        analysis.status shouldBe AnalysisStatus.NO_ACTIVITY
    }
})
