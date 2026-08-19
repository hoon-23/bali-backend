package com.bali.core.session

// 세션 진행 상태. 전이는 전부 클라이언트가 명시적으로 트리거하며 date로부터 자동 추론하지 않는다
enum class SessionStatus { SCHEDULED, IN_PROGRESS, COMPLETED }
