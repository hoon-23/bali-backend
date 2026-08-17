package com.bali.core.user

import java.util.UUID
import kotlin.math.abs

// 가입 시 사용자에게 부여할 기본 닉네임을 생성하는 순수 함수 모음
object NicknameGenerator {
    private val ADJECTIVES = listOf(
        "행복한", "용감한", "씩씩한", "즐거운", "활기찬", "상쾌한",
        "든든한", "재빠른", "우아한", "늠름한", "다정한", "차분한",
    )
    private val NOUNS = listOf(
        "옥수수", "사자", "호랑이", "감자", "다람쥐", "고양이",
        "강아지", "코끼리", "참새", "여우", "토끼", "곰돌이",
    )

    // {형용사}{명사}{userId 기반 2자리 숫자} 형태의 닉네임을 생성한다.
    // 숫자는 유일성 보장용이 아니라 장식 목적이며 userId로 결정된다(형용사/명사는 매 호출 랜덤)
    fun generate(userId: UUID): String {
        val adjective = ADJECTIVES.random()
        val noun = NOUNS.random()
        val number = abs(userId.hashCode()) % 100
        return "$adjective$noun${number.toString().padStart(2, '0')}"
    }
}
