package com.secureflow.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtActivationConfiguration.Activation.class)
class JwtActivationConfiguration {
    @ConfigurationProperties(prefix = "secureflow.jwt")
    record Activation(boolean enabled) {}
}
