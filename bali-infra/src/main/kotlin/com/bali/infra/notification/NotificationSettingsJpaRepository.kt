package com.bali.infra.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationSettingsJpaRepository : JpaRepository<NotificationSettingsJpaEntity, UUID>
