package com.secureflow.identity.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.secureflow.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {
    @Test
    void registrationNormalizesIdentityAndGrantsOnlyUser() {
        var user = User.register(new UserId(UUID.randomUUID()), new Username(" Alice "), new EmailAddress(" A@EXAMPLE.COM "));
        assertEquals(new Username("alice"), user.username());
        assertEquals(new EmailAddress("a@example.com"), user.email());
        assertEquals(Set.of(Role.USER), user.roles());
        assertEquals(AccountStatus.ACTIVE, user.status());
        assertEquals(0, user.securityVersion());
        assertThrows(UnsupportedOperationException.class, () -> user.roles().add(Role.ADMIN));
    }

    @Test
    void effectiveSecurityChangesIncrementVersionButNoOpsDoNot() {
        var user = User.register(new UserId(UUID.randomUUID()), new Username("alice"), new EmailAddress("a@example.com"));
        user.assignRole(Role.USER);
        user.assignRole(Role.ADMIN);
        user.assignRole(Role.ADMIN);
        user.changeStatus(AccountStatus.INACTIVE);
        user.changeStatus(AccountStatus.INACTIVE);
        user.removeRole(Role.ADMIN);
        user.removeRole(Role.ADMIN);
        assertEquals(3, user.securityVersion());
        assertEquals(Set.of(Role.USER), user.roles());
        assertThrows(NullPointerException.class, () -> user.assignRole(null));
        assertEquals(3, user.securityVersion());
    }

    @Test
    void invalidIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Username("ab"));
        assertThrows(IllegalArgumentException.class, () -> new Username("x".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new Username("ábc"));
        assertThrows(IllegalArgumentException.class, () -> new Username("Kbc"));
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("a..b@example.com"));
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("a@-example.com"));
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("a@example-.com"));
        assertThrows(NullPointerException.class, () -> new EmailAddress(null));
        assertThrows(NullPointerException.class, () -> new UserId(null));
    }
}
