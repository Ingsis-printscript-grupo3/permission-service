package org.snippetsearcher.permission.user

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Clock
import java.time.Instant

// el token M2M (client credentials) para la Management API. Auth0 limita cuantos se piden, asi que
// se reusa hasta que esta por vencer en vez de pedir uno por llamada
class Auth0ManagementToken(
    private val client: RestClient,
    private val audience: String,
    private val clientId: String,
    private val clientSecret: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var cached: CachedToken? = null

    @Synchronized
    fun value(): String {
        cached?.takeIf { clock.instant().isBefore(it.renewAt) }?.let { return it.value }

        val response =
            client
                .post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TokenRequest(clientId = clientId, clientSecret = clientSecret, audience = audience))
                .retrieve()
                .body<TokenResponse>()!!
        // se renueva un minuto antes de que venza, para no mandar uno que vence en el camino
        val renewAt = clock.instant().plusSeconds(response.expiresIn - RENEW_MARGIN_SECONDS)
        return CachedToken(response.accessToken, renewAt).also { cached = it }.value
    }

    private data class CachedToken(
        val value: String,
        val renewAt: Instant,
    )

    private data class TokenRequest(
        @JsonProperty("grant_type") val grantType: String = "client_credentials",
        @JsonProperty("client_id") val clientId: String,
        @JsonProperty("client_secret") val clientSecret: String,
        val audience: String,
    )

    private data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_in") val expiresIn: Long,
    )

    private companion object {
        const val RENEW_MARGIN_SECONDS = 60L
    }
}
