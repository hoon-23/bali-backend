package com.bali.batch

// 배치 러너 공통 스켈레톤: item마다 process를 실행하고, 하나가 실패해도 나머지 item은 계속 진행한다.
// 실패 시 처리(로깅, 필요하면 실패 상태 레코드 저장 등)는 호출부가 onFailure로 넘긴다 —
// 러너마다 실패 시 부수효과가 달라(예: WeeklyAnalysisRunner는 FAILED 레코드를 저장하지만
// 알림 발송 러너들은 로깅만 한다) 로깅으로만 일반화하면 그 차이가 조용히 사라질 수 있어 분리했다.
// 반환값은 Airflow가 재시도 여부를 판단하는 종료 코드 (0=전원 성공, 1=하나 이상 실패)
fun <T> runResiliently(items: List<T>, process: (T) -> Unit, onFailure: (T, Exception) -> Unit): Int {
    var hadFailure = false
    items.forEach { item ->
        try {
            process(item)
        } catch (e: Exception) {
            onFailure(item, e)
            hadFailure = true
        }
    }
    return if (hadFailure) 1 else 0
}
