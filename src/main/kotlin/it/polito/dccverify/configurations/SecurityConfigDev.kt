@Configuration
@Profile("dev-noauth")
class SecurityConfigDev {
    @Bean
    fun devFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
