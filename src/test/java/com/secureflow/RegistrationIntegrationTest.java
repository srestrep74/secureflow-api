package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.RegisterUserCommand;
import com.secureflow.identity.application.RegisterUserUseCase;
import com.secureflow.shared.application.IntegrityConflictException;
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
class RegistrationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RegisterUserUseCase register;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void validatesPasswordWithoutNormalizingIt() {
        assertThatThrownBy(() -> register.register(new RegisterUserCommand("validuser", "valid@example.com", "short")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        register.register(new RegisterUserCommand("validuser", "valid@example.com", " ".repeat(12))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        register.register(new RegisterUserCommand("validuser", "valid@example.com", "a".repeat(129))))
                .isInstanceOf(IllegalArgumentException.class);
        var password = "😀".repeat(12) + " ";
        var result = register.register(new RegisterUserCommand("unicodeuser", "unicode@example.com", password));
        var hash = jdbc.queryForObject(
                "select password_hash from user_credentials where user_id = ?",
                String.class,
                result.userId().value());
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password.trim(), hash)).isFalse();
    }

    @Test
    void registersAndEnforcesUniqueness() {
        var result =
                register.register(new RegisterUserCommand(" Alice ", "Alice@Example.com", "correct horse battery"));
        var id = result.userId().value();
        assertThat(jdbc.queryForObject("select role from user_roles where user_id = ?", String.class, id))
                .isEqualTo("USER");
        var hash =
                jdbc.queryForObject("select password_hash from user_credentials where user_id = ?", String.class, id);
        assertThat(hash).startsWith("$argon2id$").isNotEqualTo("correct horse battery");
        assertThat(encoder.matches("correct horse battery", hash)).isTrue();
        assertThatThrownBy(() -> register.register(
                        new RegisterUserCommand("ALICE", "other@example.com", "correct horse battery")))
                .isInstanceOf(IntegrityConflictException.class);
        assertThatThrownBy(() -> register.register(
                        new RegisterUserCommand("other", "ALICE@example.com", "correct horse battery")))
                .isInstanceOf(IntegrityConflictException.class);
    }

    @Test
    void rollsBackUserAndRolesWhenCredentialInsertFails() {
        jdbc.execute("alter table user_credentials add constraint test_reject_credentials check (false) not valid");
        try {
            assertThatThrownBy(() -> register.register(
                            new RegisterUserCommand("rollback", "rollback@example.com", "correct horse battery")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from users where username = 'rollback'", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "select count(*) from user_roles r left join users u on r.user_id = u.id where u.id is null",
                            Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "select count(*) from user_credentials c left join users u on c.user_id = u.id where u.id is null",
                            Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("alter table user_credentials drop constraint test_reject_credentials");
        }
        var result =
                register.register(new RegisterUserCommand("rollback", "rollback@example.com", "correct horse battery"));
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_credentials where user_id = ?",
                        Integer.class,
                        result.userId().value()))
                .isEqualTo(1);
    }
}
