package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.SecurityStateValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration(proxyBeanMethods = false)
public class BearerTestConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain testSecurity(HttpSecurity http, JwtDecoder decoder, SecurityStateValidator validator)
            throws Exception {
        return http.securityMatcher("/test/bearer/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/test/bearer/admin")
                        .hasRole("ADMIN")
                        .requestMatchers("/test/bearer/user")
                        .hasAuthority("task:read:own")
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)
                        .jwtAuthenticationConverter(new StatefulJwtAuthenticationConverter(validator))))
                .build();
    }

    @RestController
    static class TestController {
        @GetMapping({"/test/bearer/user", "/test/bearer/admin"})
        java.util.Map<String, Object> identity(Authentication auth) {
            return java.util.Map.of(
                    "id",
                    auth.getName(),
                    "authorities",
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
        }
    }
}
