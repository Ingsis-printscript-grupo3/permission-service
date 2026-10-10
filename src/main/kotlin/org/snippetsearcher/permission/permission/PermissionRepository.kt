package org.snippetsearcher.permission.permission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findByResourceTypeAndResourceIdAndUserId(
        resourceType: String,
        resourceId: String,
        userId: String,
    ): Permission?

    fun findByResourceTypeAndUserIdAndTypeIn(
        resourceType: String,
        userId: String,
        types: Collection<PermissionType>,
    ): List<Permission>

    // un solo DELETE en la base: el deleteBy derivado primero trae cada fila y despues las borra de a una
    @Modifying
    @Query("delete from Permission p where p.resourceType = :resourceType and p.resourceId = :resourceId")
    fun deleteAllOfResource(
        resourceType: String,
        resourceId: String,
    ): Int
}
