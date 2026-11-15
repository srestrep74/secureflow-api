package com.secureflow.identity.domain;

import com.secureflow.shared.domain.UserId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class User {
    private final UserId id;
    private final Username username;
    private final EmailAddress email;
    private final EnumSet<Role> roles;
    private AccountStatus status;
    private long securityVersion;

    private User(UserId id, Username username, EmailAddress email) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
        this.roles = EnumSet.of(Role.USER);
        this.status = AccountStatus.ACTIVE;
    }

    public static User register(UserId id, Username username, EmailAddress email) {
        return new User(id, username, email);
    }

    public static User reconstitute(
            UserId id,
            Username username,
            EmailAddress email,
            Set<Role> roles,
            AccountStatus status,
            long securityVersion) {
        if (securityVersion < 0) throw new IllegalArgumentException("Security version cannot be negative");
        User user = new User(id, username, email);
        user.roles.clear();
        user.roles.addAll(Objects.requireNonNull(roles, "roles"));
        user.status = Objects.requireNonNull(status, "status");
        user.securityVersion = securityVersion;
        return user;
    }

    public UserId id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public EmailAddress email() {
        return email;
    }

    public AccountStatus status() {
        return status;
    }

    public long securityVersion() {
        return securityVersion;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public void assignRole(Role role) {
        Objects.requireNonNull(role, "role");
        if (!roles.contains(role)) {
            long next = Math.incrementExact(securityVersion);
            roles.add(role);
            securityVersion = next;
        }
    }

    public void removeRole(Role role) {
        Objects.requireNonNull(role, "role");
        if (roles.contains(role)) {
            long next = Math.incrementExact(securityVersion);
            roles.remove(role);
            securityVersion = next;
        }
    }

    public void changeStatus(AccountStatus status) {
        Objects.requireNonNull(status, "status");
        if (this.status != status) {
            long next = Math.incrementExact(securityVersion);
            this.status = status;
            securityVersion = next;
        }
    }
}
