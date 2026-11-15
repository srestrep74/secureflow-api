package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.secureflow.identity.application.SecurityStateValidator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class StatefulJwtAuthenticationConverterTest {
    @Test
    void acceptsOnlyAuthoritiesAfterStateValidation() {
        var validator = mock(SecurityStateValidator.class);
        when(validator.isValid(any())).thenReturn(true);
        var id = UUID.randomUUID().toString();
        var jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject(id)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("sid", UUID.randomUUID().toString())
                .claim("security_version", 0)
                .claim("catalog_version", 1)
                .claim("roles", List.of("ROLE_USER"))
                .claim("permissions", List.of("task:read:own"))
                .build();
        var converter = new StatefulJwtAuthenticationConverter(validator);
        var authentication = converter.convert(jwt);
        assertThat(authentication.getName()).isEqualTo(id);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "task:read:own");
        verify(validator).isValid(any());
        when(validator.isValid(any())).thenReturn(false);
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }
}
