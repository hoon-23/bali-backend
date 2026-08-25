package com.bali.api.notification

import com.bali.core.notification.DevicePlatform

data class DeviceTokenRequest(val token: String, val platform: DevicePlatform)

data class DeviceTokenDeleteRequest(val token: String)
