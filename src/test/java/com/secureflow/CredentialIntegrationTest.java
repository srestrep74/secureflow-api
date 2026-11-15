package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.CredentialRepositoryPort;
import com.secureflow.identity.application.UserRepositoryPort;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class CredentialIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CredentialRepositoryPort credentials;

    @Autowired
    UserRepositoryPort users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void encodesWithRandomSaltsAndVerifiesPasswords() {
        String raw = "test password for hashing";
        String first = encoder.encode(raw);
        String second = encoder.encode(raw);
        assertThat(first).startsWith("$argon2id$").isNotEqualTo(raw).isNotEqualTo(second);
        assertThat(encoder.matches(raw, first)).isTrue();
        assertThat(encoder.matches("wrong password", first)).isFalse();
        assertThat(encoder.upgradeEncoding(first)).isFalse();
    }

    @Test
    void persistsOnlyEncodedCredentialForExistingUser() {
        var id = new UserId(UUID.randomUUID());
        String suffix = id.value().toString().substring(0, 8);
        users.create(User.register(id, new Username("user" + suffix), new EmailAddress(suffix + "@example.com")));
        String raw = "password for persistence";
        String encoded = encoder.encode(raw);
        credentials.create(id, encoded);
        assertThat(credentials.findHash(id)).contains(encoded);
        assertThat(jdbc.queryForObject(
                        "select password_hash from user_credentials where user_id = ?", String.class, id.value()))
                .isEqualTo(encoded)
                .isNotEqualTo(raw);
        assertThat(users.findById(id)).isPresent();
        assertThatThrownBy(() -> credentials.create(id, raw)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credentials.create(id, encoded))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where version = '3' and success", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsCredentialsWithoutUser() {
        assertThatThrownBy(() -> credentials.create(new UserId(UUID.randomUUID()), encoder.encode("orphan password")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
