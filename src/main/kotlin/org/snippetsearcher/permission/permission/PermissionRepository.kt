package org.snippetsearcher.permission.permission

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findByResourceTypeAndResourceIdAndUserId(
        resourceType: String,
        resourceId: String,
        userId: String,
    ): Permission?
}
