package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class NicknameGeneratorTest : StringSpec({

    "생성된 닉네임의 마지막 2글자는 숫자다" {
        val nickname = NicknameGenerator.generate(UUID.randomUUID())

        nickname.takeLast(2).all { it.isDigit() } shouldBe true
    }

    "같은 userId로 여러 번 생성해도 숫자 부분은 항상 같다" {
        val userId = UUID.randomUUID()

        val first = NicknameGenerator.generate(userId)
        val second = NicknameGenerator.generate(userId)

        first.takeLast(2) shouldBe second.takeLast(2)
    }

    "숫자 부분은 항상 2자리(00~99)다" {
        val nickname = NicknameGenerator.generate(UUID.randomUUID())

        val number = nickname.takeLast(2).toInt()
        (number in 0..99) shouldBe true
    }
})
