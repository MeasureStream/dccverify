package it.polito.dccverify.configurations
 
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
 
@Configuration
@Profile("!dev-noauth")
class SecurityConfig {
 
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // Nessuna sessione: ogni richiesta porta il proprio token.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF non serve senza sessione; il gateway lo gestisce a monte.
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health").permitAll()
                  .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth -> oauth.jwt { } }
            .build()
}
