package org.snippetsearcher.permission.support

import org.snippetsearcher.permission.user.User
import org.snippetsearcher.permission.user.UserDirectory
import org.snippetsearcher.permission.user.UserDirectoryUnavailableException
import org.snippetsearcher.permission.user.UserPage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// Auth0 en memoria, para que los tests no salgan a la red
class FakeUserDirectory : UserDirectory {
    private val users = mutableListOf<User>()
    var available = true

    init {
        reset()
    }

    fun reset() {
        users.clear()
        users +=
            listOf(
                User("auth0|ana", "Ana", "ana@example.com"),
                User("auth0|beto", "Beto", "beto@example.com"),
                User("auth0|maria", "María", "maria@example.com"),
                User("auth0|mario", "Mario", "mario@example.com"),
                User("auth0|juan", "Juan", "juan@example.com"),
            )
        available = true
    }

    override fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        pageSize: Int,
    ): UserPage {
        failIfUnavailable()
        val words = text.lowercase().split(" ")
        val matches =
            users.filter { user ->
                user.id != excludedUserId &&
                    words.all { word -> listOfNotNull(user.name, user.email).any { it.lowercase().startsWith(word) } }
            }
        return UserPage(matches.drop(page * pageSize).take(pageSize), matches.size)
    }

    override fun exists(userId: String): Boolean {
        failIfUnavailable()
        return users.any { it.id == userId }
    }

    private fun failIfUnavailable() {
        if (!available) throw UserDirectoryUnavailableException(IllegalStateException("Auth0 caido"))
    }
}

@TestConfiguration
class FakeUserDirectoryConfig {
    @Bean
    @Primary
    fun fakeUserDirectory(): FakeUserDirectory = FakeUserDirectory()
}
