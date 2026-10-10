package org.snippetsearcher.permission.permission

import org.snippetsearcher.permission.user.UserDirectory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PermissionService(
    private val repository: PermissionRepository,
    private val users: UserDirectory,
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

        // solo se verifica al compartir: el OWNER es siempre quien hace el pedido, y su token ya lo valido
        if (type == PermissionType.READ && !users.exists(userId)) throw UnknownUserException(userId)

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

    fun resources(
        resourceType: String,
        userId: String,
        relation: Relation,
    ): List<Permission> = repository.findByResourceTypeAndUserIdAndTypeIn(resourceType, userId, relation.types)

    // borrar algo que no existe no es error: si el snippet-service reintenta un borrado, no falla
    @Transactional
    fun revokeAll(
        resourceType: String,
        resourceId: String,
    ) {
        repository.deleteAllOfResource(resourceType, resourceId)
    }
}

data class GrantResult(
    val permission: Permission,
    val created: Boolean,
)

class UnknownUserException(
    userId: String,
) : RuntimeException("El usuario '$userId' no existe")

// lo que responde el check: ademas de los dos permisos, NONE cuando no tiene ninguno
enum class Access {
    OWNER,
    READ,
    NONE,
}

// que recursos se piden en el listado. Se nombran desde el usuario (los mios, los que me
// compartieron) y no con OWNER/READ, para que el permission-service decida que permiso es cada uno
enum class Relation(
    val types: Set<PermissionType>,
) {
    OWNED(setOf(PermissionType.OWNER)),
    SHARED(setOf(PermissionType.READ)),
    ALL(PermissionType.entries.toSet()),
}
