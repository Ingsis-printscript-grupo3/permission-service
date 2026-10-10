package org.snippetsearcher.permission.user

import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.permission.support.FakeUserDirectory
import org.snippetsearcher.permission.support.FakeUserDirectoryConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeUserDirectoryConfig::class)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val directory: FakeUserDirectory,
) {
    @BeforeEach
    fun reiniciarAuth0() {
        directory.reset()
    }

    @Test
    fun `sin token es un 401`() {
        mockMvc.get("/users") { param("search", "mar") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `devuelve los usuarios que empiezan con lo que se escribio`() {
        search("mar", callerId = "auth0|ana").andExpect {
            status { isOk() }
            jsonPath("$.count") { value(2) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.page_size") { value(10) }
            jsonPath("$.users[*].id") { value(containsInAnyOrder("auth0|maria", "auth0|mario")) }
            jsonPath("$.users[*].email") { value(containsInAnyOrder("maria@example.com", "mario@example.com")) }
        }
    }

    @Test
    fun `quien busca no aparece en los resultados`() {
        search("mar", callerId = "auth0|maria").andExpect {
            jsonPath("$.count") { value(1) }
            jsonPath("$.users[0].id") { value("auth0|mario") }
        }
    }

    @Test
    fun `con menos de 2 letras devuelve una lista vacia sin preguntarle a Auth0`() {
        directory.available = false

        search("m", callerId = "auth0|ana").andExpect {
            status { isOk() }
            jsonPath("$.count") { value(0) }
            jsonPath("$.users.length()") { value(0) }
        }
    }

    @Test
    fun `si Auth0 no responde es un 503`() {
        directory.available = false

        search("mar", callerId = "auth0|ana").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.error") { exists() }
        }
    }

    @Test
    fun `un page_size fuera de rango es un 400`() {
        mockMvc
            .get("/users") {
                with(jwt().jwt { it.subject("auth0|ana") })
                param("search", "mar")
                param("page_size", "500")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("El parámetro 'page_size' tiene que estar entre 1 y 50") }
            }
    }

    private fun search(
        text: String,
        callerId: String,
    ) = mockMvc.get("/users") {
        with(jwt().jwt { it.subject(callerId) })
        param("search", text)
    }
}
