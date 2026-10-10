package org.snippetsearcher.permission.permission

import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.permission.support.FakeUserDirectory
import org.snippetsearcher.permission.support.FakeUserDirectoryConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeUserDirectoryConfig::class)
class PermissionControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val repository: PermissionRepository,
    @Autowired private val users: FakeUserDirectory,
) {
    @BeforeEach
    fun limpiarLaBase() {
        repository.deleteAll()
        users.reset()
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
    fun `compartir con un usuario que no existe en Auth0 es un 422 y no guarda nada`() {
        grant(permiso("s1", "auth0|nadie", "READ")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.error") { value("El usuario 'auth0|nadie' no existe") }
        }

        assertEquals(0, repository.count())
    }

    @Test
    fun `si Auth0 no responde al compartir es un 503 y no guarda nada`() {
        users.available = false

        grant(permiso("s1", "auth0|beto", "READ")).andExpect { status { isServiceUnavailable() } }

        assertEquals(0, repository.count())
    }

    @Test
    fun `registrar al owner no le pregunta a Auth0`() {
        users.available = false

        grant(permiso("s1", "auth0|nuevo", "OWNER")).andExpect { status { isCreated() } }
    }

    @Test
    fun `reintentar un compartir que ya existe no le pregunta a Auth0`() {
        grant(permiso("s1", "auth0|beto", "READ"))
        users.available = false

        grant(permiso("s1", "auth0|beto", "READ")).andExpect { status { isOk() } }
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

    @Test
    fun `OWNED devuelve solo los recursos de los que el usuario es owner`() {
        grant(permiso("s1", "auth0|ana", "OWNER"))
        grant(permiso("s2", "auth0|ana", "READ"))

        resources("snippet", "auth0|ana", "OWNED").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].resourceId") { value("s1") }
            jsonPath("$[0].type") { value("OWNER") }
        }
    }

    @Test
    fun `SHARED devuelve solo los recursos que le compartieron`() {
        grant(permiso("s1", "auth0|ana", "OWNER"))
        grant(permiso("s2", "auth0|ana", "READ"))

        resources("snippet", "auth0|ana", "SHARED").andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].resourceId") { value("s2") }
            jsonPath("$[0].type") { value("READ") }
        }
    }

    @Test
    fun `ALL devuelve los propios y los compartidos`() {
        grant(permiso("s1", "auth0|ana", "OWNER"))
        grant(permiso("s2", "auth0|ana", "READ"))

        resources("snippet", "auth0|ana", "ALL").andExpect {
            jsonPath("$[*].resourceId") { value(containsInAnyOrder("s1", "s2")) }
        }
    }

    @Test
    fun `el listado no trae recursos de otros usuarios ni de otro tipo`() {
        grant(permiso("s1", "auth0|beto", "OWNER"))
        grant("""{"resourceType":"carpeta","resourceId":"c1","userId":"auth0|ana","type":"OWNER"}""")

        resources("snippet", "auth0|ana", "ALL").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `un relation que no existe es un 400 que dice los valores validos`() {
        resources("snippet", "auth0|ana", "MINE").andExpect {
            status { isBadRequest() }
            jsonPath(
                "$.error",
            ) { value("El parámetro 'relation' no es válido: tiene que ser uno de OWNED, SHARED, ALL") }
        }
    }

    @Test
    fun `al listado le falta el relation y es un 400`() {
        mockMvc
            .get("/internal/permissions/resources") {
                param("resourceType", "snippet")
                param("userId", "auth0|ana")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("Falta el parámetro 'relation'") }
            }
    }

    @Test
    fun `borrar los permisos de un recurso los saca a todos y no toca los de otros recursos`() {
        grant(permiso("s1", "auth0|ana", "OWNER"))
        grant(permiso("s1", "auth0|beto", "READ"))
        grant(permiso("s2", "auth0|ana", "OWNER"))

        revokeAll("snippet", "s1").andExpect { status { isNoContent() } }

        check("snippet", "s1", "auth0|ana").andExpect { jsonPath("$.type") { value("NONE") } }
        check("snippet", "s1", "auth0|beto").andExpect { jsonPath("$.type") { value("NONE") } }
        check("snippet", "s2", "auth0|ana").andExpect { jsonPath("$.type") { value("OWNER") } }
    }

    @Test
    fun `borrar los permisos de un recurso que no tiene ninguno tambien es 204`() {
        revokeAll("snippet", "no-existe").andExpect { status { isNoContent() } }
    }

    @Test
    fun `al borrado le falta el resourceId y es un 400`() {
        mockMvc
            .delete("/internal/permissions") { param("resourceType", "snippet") }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("Falta el parámetro 'resourceId'") }
            }
    }

    private fun permiso(
        resourceId: String,
        userId: String,
        type: String,
    ) = """{"resourceType":"snippet","resourceId":"$resourceId","userId":"$userId","type":"$type"}"""

    private fun resources(
        resourceType: String,
        userId: String,
        relation: String,
    ) = mockMvc.get("/internal/permissions/resources") {
        param("resourceType", resourceType)
        param("userId", userId)
        param("relation", relation)
    }

    private fun revokeAll(
        resourceType: String,
        resourceId: String,
    ) = mockMvc.delete("/internal/permissions") {
        param("resourceType", resourceType)
        param("resourceId", resourceId)
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
