package org.snippetsearcher.permission.user

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// publico: lo llama la UI (a traves de nginx) mientras el owner escribe con quien compartir.
// La respuesta tiene la forma de PaginatedUsers de la UI de la catedra, asi el adaptador no traduce nada
@RestController
@RequestMapping("/users")
class UserController(
    private val service: UserService,
) {
    @GetMapping
    fun search(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): UserPageResponse {
        val callerId = checkNotNull(jwt.subject) { "Los tokens de Auth0 siempre traen sub" }
        val result = service.search(search, callerId, page, pageSize)
        return UserPageResponse(
            page,
            pageSize,
            result.total,
            result.users.map { UserResponse(it.id, it.name, it.email) },
        )
    }
}

data class UserPageResponse(
    val page: Int,
    @JsonProperty("page_size") val pageSize: Int,
    val count: Int,
    val users: List<UserResponse>,
)

data class UserResponse(
    val id: String,
    val name: String,
    val email: String?,
)
