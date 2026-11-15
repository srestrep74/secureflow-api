package com.secureflow.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginDtoTest {
    @Test
    void redactsCredentials() {
        assertThat(new LoginRequest("sample", "secret-password").toString())
                .doesNotContain("sample", "secret-password");
        assertThat(new LoginResponse("access-secret", "refresh-secret", "Bearer", 300).toString())
                .doesNotContain("access-secret", "refresh-secret");
    }
}
