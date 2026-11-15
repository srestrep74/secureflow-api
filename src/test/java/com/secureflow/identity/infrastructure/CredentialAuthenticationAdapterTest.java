package com.secureflow.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.secureflow.identity.application.*;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class CredentialAuthenticationAdapterTest {
    @Test
    void delegatesToManagerAndSeparatesInfrastructureFailure() {
        var users = mock(UserRepositoryPort.class);
        var credentials = mock(CredentialRepositoryPort.class);
        var encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        var user = User.register(
                new UserId(UUID.randomUUID()), new Username("alice"), new EmailAddress("alice@example.com"));
        when(users.findByUsername(new Username("alice"))).thenReturn(Optional.of(user));
        when(credentials.findHash(user.id())).thenReturn(Optional.of(encoder.encode("a strong password")));
        var provider = new DaoAuthenticationProvider(new CredentialUserDetailsService(users, credentials));
        provider.setPasswordEncoder(encoder);
        var manager = spy(new ProviderManager(provider));
        var adapter = new SpringCredentialAuthenticationAdapter(manager);
        assertThat(adapter.authenticate("alice", "a strong password").userId()).isEqualTo(user.id());
        verify(manager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        when(users.findByUsername(new Username("alice")))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> adapter.authenticate("alice", "a strong password"))
                .isInstanceOf(AuthenticationUnavailableException.class)
                .hasMessage("Authentication unavailable");
        doThrow(new IllegalStateException("unexpected failure")).when(manager).authenticate(any());
        assertThatThrownBy(() -> adapter.authenticate("alice", "a strong password"))
                .isInstanceOf(IllegalStateException.class);
        doCallRealMethod().when(manager).authenticate(any());
        doReturn(Optional.empty()).when(users).findByUsername(new Username("alice"));
        assertThatThrownBy(() -> adapter.authenticate("alice", "a strong password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
    }
}
