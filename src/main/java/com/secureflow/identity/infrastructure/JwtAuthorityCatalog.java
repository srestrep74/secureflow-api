package com.secureflow.identity.infrastructure;

import com.secureflow.identity.domain.Role;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class JwtAuthorityCatalog {
    static final long VERSION = 1;
    private static final Map<Role, Set<String>> GRANTS = Map.of(
            Role.USER, Set.of("task:create", "task:read:own", "task:update:own", "task:delete:own"),
            Role.AUDITOR, Set.of("task:read:any"),
            Role.ADMIN,
                    Set.of(
                            "task:create",
                            "task:read:any",
                            "task:update:any",
                            "task:delete:any",
                            "user:read:any",
                            "user:role:assign",
                            "user:status:update"));

    private JwtAuthorityCatalog() {}

    static Set<String> permissions(Set<Role> roles) {
        return roles.stream().flatMap(role -> GRANTS.get(role).stream()).collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(role -> "ROLE_" + role.name()).collect(Collectors.toUnmodifiableSet());
    }
}
