package org.snippetsearcher.permission.user

import org.snippetsearcher.permission.common.InvalidRequestException
import org.springframework.stereotype.Service

@Service
class UserService(
    private val directory: UserDirectory,
) {
    // la UI busca mientras se escribe: con menos de 2 letras no se le pregunta a Auth0, que tiene
    // rate limit y devolveria a casi todos. Quien busca nunca aparece: no puede compartir consigo mismo
    fun search(
        text: String,
        callerId: String,
        page: Int,
        pageSize: Int,
    ): UserPage {
        if (page < 0) throw InvalidRequestException("El parámetro 'page' no puede ser negativo")
        if (pageSize !in 1..MAX_PAGE_SIZE) {
            throw InvalidRequestException("El parámetro 'page_size' tiene que estar entre 1 y $MAX_PAGE_SIZE")
        }
        if (text.trim().length < MIN_SEARCH_LENGTH) return UserPage.EMPTY

        return directory.search(text.trim(), callerId, page, pageSize)
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_PAGE_SIZE = 50
    }
}
