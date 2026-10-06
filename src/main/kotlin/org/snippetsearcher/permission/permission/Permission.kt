package org.snippetsearcher.permission.permission

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// que puede hacer un usuario sobre un recurso. El recurso es generico: un tipo y un id
@Entity
@Table(name = "permission")
class Permission(
    @Id
    val id: UUID,
    val resourceType: String,
    val resourceId: String,
    val userId: String,
    @Enumerated(EnumType.STRING)
    val type: PermissionType,
    val createdAt: Instant,
)

// OWNER incluye todo lo que puede READ
enum class PermissionType {
    OWNER,
    READ,
}
