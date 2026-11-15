package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.*;
import com.secureflow.identity.domain.User;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class LoginConfiguration {
    @Bean
    LoginTokenPort loginTokenPort(RefreshTokenSecrets secrets, AccessTokenIssuer issuer) {
        return new LoginTokenPort() {
            public String generateRefreshToken() {
                return secrets.generate();
            }

            public String digestRefreshToken(String token) {
                return secrets.digest(token);
            }

            public String issueAccessToken(User user, SessionFamily family) {
                return issuer.issue(new AccessTokenClaims(
                        user.id().value(),
                        family.id(),
                        user.securityVersion(),
                        JwtAuthorityCatalog.VERSION,
                        user.roles()));
            }
        };
    }

    @Bean
    LoginTransactionPort loginTransactionPort(PlatformTransactionManager manager, JdbcTemplate jdbc) {
        var template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return (id, operation) -> template.execute(status -> {
            var found = jdbc.query(
                    "select id from users where id = ? for update", (rs, row) -> rs.getObject(1), id.value());
            if (found.isEmpty()) throw new InvalidCredentialsException();
            return operation.get();
        });
    }

    @Bean
    LoginUseCase loginUseCase(
            AuthenticateCredentialsUseCase authentication,
            UserRepositoryPort users,
            SessionRepositoryPort sessions,
            LoginTokenPort tokens,
            LoginTransactionPort transaction) {
        return new LoginUseCase(authentication, users, sessions, tokens, transaction, Clock.systemUTC());
    }
}
