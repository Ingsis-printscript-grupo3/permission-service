package org.snippetsearcher.permission.permission

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// /internal: solo lo llama el snippet-service, nunca se expone a la UI
@RestController
@RequestMapping("/internal/permissions")
class PermissionController(
    private val service: PermissionService,
) {
    // 201 si el permiso es nuevo, 200 si ya existia
    @PostMapping
    fun grant(
        @RequestBody request: GrantRequest,
    ): ResponseEntity<PermissionResponse> {
        request.requireNoBlanks()
        val result = service.grant(request.resourceType, request.resourceId, request.userId, request.type)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(PermissionResponse.from(result.permission))
    }

    @GetMapping("/check")
    fun check(
        @RequestParam resourceType: String,
        @RequestParam resourceId: String,
        @RequestParam userId: String,
    ): CheckResponse = CheckResponse(service.check(resourceType, resourceId, userId))
}

data class GrantRequest(
    val resourceType: String,
    val resourceId: String,
    val userId: String,
    val type: PermissionType,
) {
    fun requireNoBlanks() {
        val blank =
            listOf("resourceType" to resourceType, "resourceId" to resourceId, "userId" to userId)
                .firstOrNull { (_, value) -> value.isBlank() }
        if (blank != null) throw InvalidRequestException("El campo '${blank.first}' no puede estar vacío")
    }
}

data class PermissionResponse(
    val id: UUID,
    val resourceType: String,
    val resourceId: String,
    val userId: String,
    val type: PermissionType,
) {
    companion object {
        fun from(permission: Permission): PermissionResponse =
            with(permission) { PermissionResponse(id, resourceType, resourceId, userId, type) }
    }
}

data class CheckResponse(
    val type: Access,
)

class InvalidRequestException(
    message: String,
) : RuntimeException(message)
