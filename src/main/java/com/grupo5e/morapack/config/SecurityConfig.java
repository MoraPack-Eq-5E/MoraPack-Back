package com.grupo5e.morapack.config;

import com.grupo5e.morapack.api.auth.JwtAuthFilter;
import com.grupo5e.morapack.api.auth.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * Configuración de seguridad de la aplicación.
 * Define la autenticación JWT, CORS y reglas de acceso a endpoints.
 */
@Configuration
public class SecurityConfig {

    /**
     * Configura el generador de tokens JWT.
     * Lee la configuración desde application.properties.
     */
    @Bean
    public JwtUtil jwtUtil(org.springframework.core.env.Environment env) {
        String secret = env.getProperty("jwt.secret", "dev-secret-32-chars-minimo-1234567890");
        long exp = Long.parseLong(env.getProperty("jwt.expiration.ms", "86400000"));
        return new JwtUtil(secret, exp);
    }

    /**
     * Encoder BCrypt para encriptar contraseñas.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configura la cadena de filtros de seguridad.
     * Define CORS, autenticación JWT y reglas de acceso a endpoints.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwt) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(req -> {
                    CorsConfiguration c = new CorsConfiguration();
                    c.setAllowedOrigins(List.of("http://localhost:5173","http://127.0.0.1:5173","http://localhost:5174"));
                    c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
                    c.setAllowedHeaders(List.of("Authorization","Content-Type"));
                    c.setAllowCredentials(true);
                    return c;
                }))
                .authorizeHttpRequests(auth -> auth
                        // Permitir Swagger y OpenAPI sin autenticación
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Permitir login sin autenticación
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Requerir autenticación para /me
                        .requestMatchers(HttpMethod.GET,  "/api/auth/me").authenticated()
                        // Permitir todo lo demás (puedes cambiar a .authenticated() si quieres proteger todas las APIs)
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults());

        // Registra el filtro ANTES del filtro de username/password
        http.addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
