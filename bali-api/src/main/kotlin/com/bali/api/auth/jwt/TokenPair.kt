package com.bali.api.auth.jwt

// 발급된 access token(JWT) + refresh token(opaque) 쌍
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)
