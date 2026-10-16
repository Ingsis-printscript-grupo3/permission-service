package org.snippetsearcher.permission.user

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

// busca y verifica usuarios con la Management API de Auth0 (necesita el scope read:users)
class Auth0UserDirectory(
    private val client: RestClient,
    private val token: Auth0ManagementToken,
) : UserDirectory {
    override fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        pageSize: Int,
    ): UserPage {
        val query = searchQuery(text, excludedUserId) ?: return UserPage.EMPTY
        val response = callAuth0 { fetchUsersPage(query, page, pageSize) }
        return UserPage(response.users.map { it.toUser() }, response.total)
    }

    private fun fetchUsersPage(
        query: String,
        page: Int,
        pageSize: Int,
    ): Auth0UsersPage =
        client
            .get()
            .uri { uri ->
                uri
                    .path("/api/v2/users")
                    .queryParam("q", "{q}")
                    .queryParam("search_engine", "v3")
                    .queryParam("page", page)
                    .queryParam("per_page", pageSize)
                    .queryParam("include_totals", true)
                    .queryParam("fields", "user_id,name,email,nickname")
                    // el query va como variable para que se codifique entero (comillas, |, espacios)
                    .build(mapOf("q" to query))
            }.header("Authorization", "Bearer ${token.value()}")
            .retrieve()
            .body<Auth0UsersPage>()!!

    @Suppress("SwallowedException")
    override fun exists(userId: String): Boolean =
        callAuth0 {
            // el token se pide fuera del try: un 404 al pedirlo es un problema de configuracion, no del usuario
            val bearer = "Bearer ${token.value()}"
            try {
                client
                    .get()
                    .uri("/api/v2/users/{id}?fields=user_id", userId)
                    .header("Authorization", bearer)
                    .retrieve()
                    .toBodilessEntity()
                true
            } catch (e: HttpClientErrorException.NotFound) {
                false
            }
        }

    // cualquier otra falla (red, 429, credenciales mal) se puede reintentar: no dice nada del usuario
    private fun <T> callAuth0(call: () -> T): T =
        try {
            call()
        } catch (e: RestClientException) {
            throw UserDirectoryUnavailableException(e)
        }

    private data class Auth0UsersPage(
        val total: Int,
        val users: List<Auth0User>,
    )

    private data class Auth0User(
        @JsonProperty("user_id") val userId: String,
        val name: String?,
        val email: String?,
        val nickname: String?,
    ) {
        fun toUser() = User(userId, name ?: nickname ?: email ?: userId, email)
    }
}

// cada palabra tiene que ser el principio del email, el nombre o el nickname. Se sacan los caracteres
// que la sintaxis de busqueda de Auth0 (Lucene) interpreta, asi lo que escribe el usuario no cambia
// la consulta. null si no queda ninguna palabra para buscar
internal fun searchQuery(
    text: String,
    excludedUserId: String,
): String? {
    val words =
        text
            .split(WHITESPACE)
            .map { it.replace(NOT_SEARCHABLE, "").lowercase() }
            .filter { it.isNotEmpty() }
    if (words.isEmpty()) return null

    val matches = words.joinToString(" AND ") { "(email:$it* OR name:$it* OR nickname:$it*)" }
    return "$matches AND NOT user_id:\"${excludedUserId.replace("\"", "")}\""
}

private val WHITESPACE = Regex("\\s+")
private val NOT_SEARCHABLE = Regex("[^\\p{L}\\p{N}@._]")
