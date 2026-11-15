package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.SecurityStateValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Value("${secureflow.jwt.enabled:false}") boolean loginEnabled,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean uiEnabled,
            ObjectProvider<JwtDecoder> decoder,
            SecurityStateValidator validator)
            throws Exception {
        var bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        var deniedHandler = new BearerTokenAccessDeniedHandler();
        org.springframework.security.web.AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            if (exception instanceof StatefulJwtAuthenticationConverter.SecurityStateAuthenticationException) {
                response.setStatus(503);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":503,\"title\":\"Service Unavailable\"}");
            } else {
                bearerEntryPoint.commence(request, response, exception);
                response.setContentType("application/problem+json");
                if (response.getStatus() == 400) {
                    response.getWriter().write("{\"status\":400,\"title\":\"Bad Request\"}");
                } else {
                    response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
                }
            }
        };
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, exception) -> {
                            deniedHandler.handle(request, response, exception);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\"}");
                        }))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                            .permitAll();
                    if (loginEnabled) {
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                                .permitAll();
                        auth.requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated();
                    }
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    if (docsEnabled)
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**")
                                .permitAll();
                    if (docsEnabled && uiEnabled)
                        auth.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**")
                                .permitAll();
                    auth.anyRequest().denyAll();
                });
        if (loginEnabled) {
            http.oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(entryPoint)
                    .jwt(jwt -> jwt.decoder(decoder.getObject())
                            .jwtAuthenticationConverter(new StatefulJwtAuthenticationConverter(validator))));
        }
        return http.build();
    }
}
