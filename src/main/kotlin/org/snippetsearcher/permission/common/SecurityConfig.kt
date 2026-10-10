package org.snippetsearcher.permission.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

// el servicio es un resource server: valida el JWT de Auth0 (firma con el JWKS, iss, aud y exp) y
// toma al usuario del claim sub. No hay sesiones ni cookies, asi que tampoco hace falta CSRF
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                // /internal/** solo es alcanzable en la red interna (nginx no lo expone). Pedir el token
                // aca tambien queda para cuando el snippet-service lo propague: hoy manda X-User-Id
                authorize("/internal/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer { jwt { } }
        }
        return http.build()
    }
}
