package org.snippetsearcher.permission.user

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class Auth0Config {
    @Bean
    fun userDirectory(
        builder: RestClient.Builder,
        @Value("\${auth0.domain}") domain: String,
        @Value("\${auth0.m2m.client-id}") clientId: String,
        @Value("\${auth0.m2m.client-secret}") clientSecret: String,
    ): UserDirectory {
        // timeouts cortos: la UI busca mientras se escribe y es mejor un 503 rapido que colgarla
        val timeouts =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(2))
                setReadTimeout(Duration.ofSeconds(5))
            }
        val client =
            builder
                .clone()
                .baseUrl("https://$domain")
                .requestFactory(timeouts)
                .build()
        val token = Auth0ManagementToken(client, "https://$domain/api/v2/", clientId, clientSecret)
        return Auth0UserDirectory(client, token)
    }
}
