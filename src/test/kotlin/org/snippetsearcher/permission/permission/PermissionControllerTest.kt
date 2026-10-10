package org.snippetsearcher.permission.permission

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val repository: PermissionRepository,
) {
    @BeforeEach
    fun limpiarLaBase() {
        repository.deleteAll()
    }

    @Test
    fun `registrar al owner devuelve 201 y despues el check dice OWNER`() {
        grant("""{"resourceType":"snippet","resourceId":"s1","userId":"auth0|ana","type":"OWNER"}""")
            .andExpect {
                status { isCreated() }
                jsonPath("$.type") { value("OWNER") }
            }

        check("snippet", "s1", "auth0|ana").andExpect { jsonPath("$.type") { value("OWNER") } }
    }

    @Test
    fun `un usuario con permiso de lectura recibe READ en el check`() {
        grant("""{"resourceType":"snippet","resourceId":"s1","userId":"auth0|beto","type":"READ"}""")

        check("snippet", "s1", "auth0|beto").andExpect { jsonPath("$.type") { value("READ") } }
    }

    @Test
    fun `sin permiso el check responde NONE`() {
        check("snippet", "s1", "auth0|nadie").andExpect {
            status { isOk() }
            jsonPath("$.type") { value("NONE") }
        }
    }

    @Test
    fun `tener permiso sobre un recurso no da acceso a otro`() {
        grant("""{"resourceType":"snippet","resourceId":"s1","userId":"auth0|ana","type":"OWNER"}""")

        check("snippet", "s2", "auth0|ana").andExpect { jsonPath("$.type") { value("NONE") } }
    }

    @Test
    fun `registrar dos veces el mismo permiso no lo duplica`() {
        val body = """{"resourceType":"snippet","resourceId":"s1","userId":"auth0|ana","type":"OWNER"}"""
        grant(body).andExpect { status { isCreated() } }

        grant(body).andExpect { status { isOk() } }

        assertEquals(1, repository.count())
    }

    @Test
    fun `un type que no es OWNER ni READ es un 400`() {
        grant("""{"resourceType":"snippet","resourceId":"s1","userId":"auth0|ana","type":"ADMIN"}""")
            .andExpect { status { isBadRequest() } }

        assertEquals(0, repository.count())
    }

    @Test
    fun `si falta un campo es un 400`() {
        grant("""{"resourceType":"snippet","resourceId":"s1","type":"OWNER"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `un campo vacio es un 400 que dice cual es`() {
        grant("""{"resourceType":"snippet","resourceId":"","userId":"auth0|ana","type":"OWNER"}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("El campo 'resourceId' no puede estar vacío") }
            }
    }

    @Test
    fun `al check le falta un parametro y es un 400`() {
        mockMvc
            .get("/internal/permissions/check") {
                param("resourceType", "snippet")
                param("resourceId", "s1")
            }.andExpect { status { isBadRequest() } }
    }

    private fun grant(body: String) =
        mockMvc.post("/internal/permissions") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun check(
        resourceType: String,
        resourceId: String,
        userId: String,
    ) = mockMvc.get("/internal/permissions/check") {
        param("resourceType", resourceType)
        param("resourceId", resourceId)
        param("userId", userId)
    }
}
