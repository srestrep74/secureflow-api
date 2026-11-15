package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.SecurityStatePort;
import com.secureflow.identity.application.SecurityStateValidator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SecurityStateConfiguration {
    @Bean
    SecurityStateValidator securityStateValidator(SecurityStatePort port) {
        return new SecurityStateValidator(port, Clock.systemUTC());
    }
}
