package com.bali.core.notification

// 영수증(receipt) 조회 결과를 담는 기록용 상태. v1은 이 상태를 갱신하는 로직이 없어 항상 PENDING으로 남는다
enum class DeliveryStatus { PENDING, DELIVERED, FAILED }
