package com.secureflow.identity.domain;

import com.secureflow.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserReconstitutionTest {
    @Test
    void preservesStateAndRejectsNegativeVersion() {
        var id = new UserId(UUID.randomUUID());
        var name = new Username("Example");
        var email = new EmailAddress("Example@Example.com");
        var user = User.reconstitute(id, name, email, Set.of(Role.ADMIN), AccountStatus.INACTIVE, 3);
        assertThat(user.roles()).containsExactly(Role.ADMIN);
        assertThat(user.status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(user.securityVersion()).isEqualTo(3);
        assertThatThrownBy(() -> User.reconstitute(id, name, email, Set.of(), AccountStatus.ACTIVE, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
