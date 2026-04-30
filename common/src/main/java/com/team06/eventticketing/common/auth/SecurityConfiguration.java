package com.team06.eventticketing.common.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(401, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication = org.springframework.security.core.context.SecurityContextHolder
                                    .getContext()
                                    .getAuthentication();
                            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                                response.sendError(401, "Authentication required");
                            } else {
                                response.sendError(403, "Forbidden");
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health", "/health", "/error")
                        .permitAll()
                        .requestMatchers(
                                PathPatternRequestMatcher.pathPattern("/api/{service}/health"),
                                PathPatternRequestMatcher.pathPattern("/api/{segment1}/{segment2}/health"),
                                PathPatternRequestMatcher.pathPattern("/{*path}/health"))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
