package com.bali.infra.analysis

import com.bali.core.analysis.AnalysisSummary
import com.bali.core.exercise.MuscleGroup
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AnalysisSummaryJsonCompatTest {

    // 맨몸 지표 도입 전에 저장된 summary JSON이 기본값으로 역직렬화되는지 확인
    @Test
    fun `신규 필드가 없는 과거 summary JSON도 기본값으로 읽힌다`() {
        val oldJson = """
            {"totalWorkoutMinutes":60,"volumeByExercise":{},"volumeByMuscleGroup":{"CHEST":1000.0},
             "cardioTotalMinutes":0,"completionRate":100.0,"volumeChangeFromLastWeekPercent":null}
        """.trimIndent()

        val summary = jacksonObjectMapper().readValue(oldJson, AnalysisSummary::class.java)

        assertEquals(60, summary.totalWorkoutMinutes)
        assertEquals(0, BigDecimal("1000.0").compareTo(summary.volumeByMuscleGroup[MuscleGroup.CHEST]))
        assertEquals(emptyMap<Any, Any>(), summary.bodyweightRepsByExercise)
        assertEquals(emptyMap<Any, Any>(), summary.setsByMuscleGroup)
        assertEquals(null, summary.bodyweightRepsChangeFromLastWeekPercent)
    }
}
