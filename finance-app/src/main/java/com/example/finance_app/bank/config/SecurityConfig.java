package com.example.finance_app.bank.config;

import com.example.finance_app.bank.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS must be enabled here (not just at MVC level) so Spring Security
            // allows preflight OPTIONS requests before authentication kicks in
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // REST API — no CSRF needed (stateless, no browser session)
            .csrf(AbstractHttpConfigurer::disable)

            // No server-side sessions — every request must carry credentials
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Preflight OPTIONS requests must be permitted without auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Health check open to all (load balancers, monitoring)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // User registration is public — no credentials needed to sign up
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/register").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated())

            // HTTP Basic Auth — client sends Authorization: Basic <base64(user:pass)>
            .httpBasic(Customizer.withDefaults())

            .userDetailsService(userDetailsService);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins — Angular dev server + production domain
        // In production: replace with your actual deployed frontend URL
        config.setAllowedOrigins(List.of(
            "http://localhost:4200"   // Angular dev server
        ));

        // Standard HTTP methods used by the REST API
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow Authorization header (needed for Basic Auth) + Content-Type
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));

        // Allow browser to read response headers (e.g. for pagination cursors if added later)
        config.setExposedHeaders(List.of("Content-Type"));

        // Do not send cookies — stateless API
        config.setAllowCredentials(false);

        // Cache preflight response for 1 hour (reduces OPTIONS requests)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
