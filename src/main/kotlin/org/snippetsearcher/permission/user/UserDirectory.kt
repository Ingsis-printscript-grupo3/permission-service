package org.snippetsearcher.permission.user

// de donde salen los usuarios. Hoy es Auth0, pero el resto del servicio no lo sabe: los tests usan
// uno en memoria y cambiar de proveedor es escribir otra implementacion
interface UserDirectory {
    // los usuarios cuyo email, nombre o nickname empieza con cada palabra de text, sin excludedUserId
    fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        pageSize: Int,
    ): UserPage

    fun exists(userId: String): Boolean
}

data class User(
    val id: String,
    val name: String,
    val email: String?,
)

// total es la cantidad de usuarios que coinciden, no los de esta pagina
data class UserPage(
    val users: List<User>,
    val total: Int,
) {
    companion object {
        val EMPTY = UserPage(emptyList(), 0)
    }
}

// el proveedor no respondio, respondio con error o con 429: el pedido se puede reintentar
class UserDirectoryUnavailableException(
    cause: Throwable,
) : RuntimeException("No se pudo consultar el directorio de usuarios", cause)
