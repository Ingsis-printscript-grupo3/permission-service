package org.snippetsearcher.permission.permission

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class PermissionService(
    private val repository: PermissionRepository,
) {
    // si el usuario ya tenia un permiso sobre el recurso, no se crea otro: se devuelve el que habia.
    // Asi el snippet-service puede reintentar sin miedo a duplicar.
    fun grant(
        resourceType: String,
        resourceId: String,
        userId: String,
        type: PermissionType,
    ): GrantResult {
        val existing = repository.findByResourceTypeAndResourceIdAndUserId(resourceType, resourceId, userId)
        if (existing != null) return GrantResult(existing, created = false)

        val permission = Permission(UUID.randomUUID(), resourceType, resourceId, userId, type, Instant.now())
        return GrantResult(repository.save(permission), created = true)
    }

    fun check(
        resourceType: String,
        resourceId: String,
        userId: String,
    ): Access {
        val permission = repository.findByResourceTypeAndResourceIdAndUserId(resourceType, resourceId, userId)
        return when (permission?.type) {
            PermissionType.OWNER -> Access.OWNER
            PermissionType.READ -> Access.READ
            null -> Access.NONE
        }
    }
}

data class GrantResult(
    val permission: Permission,
    val created: Boolean,
)

// lo que responde el check: ademas de los dos permisos, NONE cuando no tiene ninguno
enum class Access {
    OWNER,
    READ,
    NONE,
}
