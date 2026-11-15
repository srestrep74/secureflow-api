package com.secureflow.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Value("${secureflow.jwt.enabled:false}") boolean loginEnabled,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean uiEnabled)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                            .permitAll();
                    if (loginEnabled)
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                                .permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    if (docsEnabled)
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**")
                                .permitAll();
                    if (docsEnabled && uiEnabled)
                        auth.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**")
                                .permitAll();
                    auth.anyRequest().denyAll();
                });
        return http.build();
    }
}
