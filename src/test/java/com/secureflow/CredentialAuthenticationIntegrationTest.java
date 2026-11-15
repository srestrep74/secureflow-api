package com.secureflow;

import static org.assertj.core.api.Assertions.*;

import com.secureflow.identity.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class CredentialAuthenticationIntegrationTest {
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
    AuthenticateCredentialsUseCase authenticate;

    @Autowired
    AuthenticationManager manager;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void authenticatesThroughRealProviderWithoutExposingCredentials() {
        var user =
                register.register(new RegisterUserCommand("loginuser", "login@example.com", "correct horse battery"));
        var command = new AuthenticateCredentialsCommand(" LOGINUSER ", "correct horse battery");
        var result = authenticate.authenticate(command);
        assertThat(result.userId()).isEqualTo(user.userId());
        assertThat(result.username()).isEqualTo("loginuser");
        assertThat(result.toString()).doesNotContain("correct horse battery", "$argon2");
        assertThat(command.toString()).doesNotContain("correct horse battery");
        assertThat(authenticate
                        .authenticate(
                                new AuthenticateCredentialsCommand(" LOGIN@EXAMPLE.COM ", "correct horse battery"))
                        .userId())
                .isEqualTo(user.userId());
        var springResult = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("loginuser", "correct horse battery"));
        assertThat(springResult.getName()).isEqualTo("loginuser");
        assertThat(springResult.getAuthorities()).extracting("authority").contains("ROLE_USER");
        assertThat(springResult.getCredentials()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(jdbc.queryForObject(
                        "select password_hash from user_credentials where user_id = ?",
                        String.class,
                        user.userId().value()))
                .startsWith("$argon2id$");
    }

    @Test
    void concurrentAttemptsRemainIsolated() throws Exception {
        var first = register.register(
                new RegisterUserCommand("parallelone", "parallelone@example.com", "first long password"));
        var second = register.register(
                new RegisterUserCommand("paralleltwo", "paralleltwo@example.com", "second long password"));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var a = executor.submit(() -> {
                gate.await();
                var result = authenticate.authenticate(
                        new AuthenticateCredentialsCommand("parallelone", "first long password"));
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isNull();
                return result;
            });
            var b = executor.submit(() -> {
                gate.await();
                var result = authenticate.authenticate(
                        new AuthenticateCredentialsCommand("paralleltwo", "second long password"));
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isNull();
                return result;
            });
            gate.countDown();
            assertThat(a.get().userId()).isEqualTo(first.userId());
            assertThat(b.get().userId()).isEqualTo(second.userId());
        }
    }

    @Test
    void rejectsInvalidCredentialsAndInactiveOrIncompleteAccounts() {
        var user = register.register(
                new RegisterUserCommand("failureuser", "failure@example.com", "correct horse battery"));
        var bad = catchThrowable(() ->
                authenticate.authenticate(new AuthenticateCredentialsCommand("failureuser", "incorrect password")));
        var missing = catchThrowable(() ->
                authenticate.authenticate(new AuthenticateCredentialsCommand("unknownuser", "incorrect password")));
        assertThat(bad).isInstanceOf(InvalidCredentialsException.class).hasMessage(missing.getMessage());
        assertThat(missing).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authenticate.authenticate(new AuthenticateCredentialsCommand("", "password")))
                .isInstanceOf(InvalidCredentialsException.class);
        jdbc.update(
                "update users set status = 'INACTIVE' where id = ?",
                user.userId().value());
        assertThatThrownBy(() -> authenticate.authenticate(
                        new AuthenticateCredentialsCommand("failureuser", "correct horse battery")))
                .isInstanceOf(InvalidCredentialsException.class);
        jdbc.update(
                "update users set status = 'ACTIVE' where id = ?", user.userId().value());
        jdbc.update(
                "delete from user_credentials where user_id = ?", user.userId().value());
        assertThatThrownBy(() -> authenticate.authenticate(
                        new AuthenticateCredentialsCommand("failureuser", "correct horse battery")))
                .isInstanceOf(AuthenticationUnavailableException.class);
    }
}
