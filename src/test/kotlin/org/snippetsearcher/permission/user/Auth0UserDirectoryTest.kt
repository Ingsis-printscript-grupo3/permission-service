package org.snippetsearcher.permission.user

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// el adaptador contra un Auth0 simulado: que pregunta, con que token y como traduce las respuestas
class Auth0UserDirectoryTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var directory: Auth0UserDirectory

    @BeforeEach
    fun armarAuth0Simulado() {
        val builder = RestClient.builder().baseUrl(AUTH0)
        server = MockRestServiceServer.bindTo(builder).build()
        val client = builder.build()
        directory = Auth0UserDirectory(client, Auth0ManagementToken(client, "$AUTH0/api/v2/", "id", "secret"))
    }

    @Test
    fun `busca por prefijo en email, nombre y nickname sin incluir a quien busca`() {
        expectToken()
        val query = "(email:mar* OR name:mar* OR nickname:mar*) AND NOT user_id:\"auth0|ana\""
        server
            .expect(requestTo(startsWith("$AUTH0/api/v2/users?")))
            .andExpect { request -> assertEquals(query, decodedParam(request.uri, "q")) }
            .andExpect(queryParam("page", "1"))
            .andExpect(queryParam("per_page", "5"))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andRespond(
                withSuccess(
                    """{"total":7,"users":[{"user_id":"auth0|maria","name":"María","email":"maria@example.com"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val page = directory.search("Mar", "auth0|ana", page = 1, pageSize = 5)

        assertEquals(7, page.total)
        assertEquals(listOf(User("auth0|maria", "María", "maria@example.com")), page.users)
        server.verify()
    }

    @Test
    fun `el token se pide una vez y se reusa mientras no venza`() {
        expectToken()
        repeat(2) {
            server
                .expect(requestToUriTemplate("$AUTH0/api/v2/users/{id}?fields=user_id", "auth0|ana"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""{"user_id":"auth0|ana"}""", MediaType.APPLICATION_JSON))
        }

        assertTrue(directory.exists("auth0|ana"))
        assertTrue(directory.exists("auth0|ana"))
        server.verify()
    }

    @Test
    fun `un usuario que Auth0 no encuentra no existe`() {
        expectToken()
        server
            .expect(requestToUriTemplate("$AUTH0/api/v2/users/{id}?fields=user_id", "auth0|nadie"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFalse(directory.exists("auth0|nadie"))
    }

    @Test
    fun `un 429 de Auth0 es que no esta disponible, no que el usuario no existe`() {
        expectToken()
        server
            .expect(requestToUriTemplate("$AUTH0/api/v2/users/{id}?fields=user_id", "auth0|ana"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertFailsWith<UserDirectoryUnavailableException> { directory.exists("auth0|ana") }
    }

    @Test
    fun `si no se consigue el token no esta disponible, aunque Auth0 responda 404`() {
        // un tenant mal configurado responde 404 al pedir el token: no significa que el usuario no exista
        server.expect(requestTo("$AUTH0/oauth/token")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<UserDirectoryUnavailableException> { directory.exists("auth0|ana") }
    }

    @Test
    fun `los caracteres de la sintaxis de busqueda se descartan y cada palabra se busca aparte`() {
        assertEquals(
            "(email:maría* OR name:maría* OR nickname:maría*) AND (email:lo* OR name:lo* OR nickname:lo*)" +
                " AND NOT user_id:\"auth0|ana\"",
            searchQuery("María)  lo*", "auth0|ana"),
        )
    }

    @Test
    fun `si no queda ninguna palabra no hay consulta`() {
        assertNull(searchQuery("*) (", "auth0|ana"))
    }

    private fun expectToken() {
        server
            .expect(once(), requestTo("$AUTH0/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess("""{"access_token":"token-1","expires_in":86400}""", MediaType.APPLICATION_JSON),
            )
    }

    private fun decodedParam(
        uri: URI,
        name: String,
    ): String =
        URLDecoder.decode(
            UriComponentsBuilder
                .fromUri(uri)
                .build()
                .queryParams
                .getFirst(name),
            Charsets.UTF_8,
        )

    private companion object {
        const val AUTH0 = "https://tenant.auth0.test"
    }
}
