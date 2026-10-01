package com.bali.core.session

// 세션 진행 상태. 전이는 클라이언트가 명시적으로 트리거하되, 자정(KST)을 넘긴 IN_PROGRESS만 배치가 ABANDONED(중단)로 전환한다
enum class SessionStatus { SCHEDULED, IN_PROGRESS, COMPLETED, ABANDONED }
