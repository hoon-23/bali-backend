package com.bali.api.auth.login

// refresh token이 존재하지 않거나, 만료/폐기되어 재발급에 사용할 수 없을 때 던진다
class InvalidRefreshTokenException(message: String) : RuntimeException(message)
