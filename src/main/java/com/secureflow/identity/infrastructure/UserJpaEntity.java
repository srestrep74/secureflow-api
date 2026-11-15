package com.secureflow.identity.infrastructure;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "users")
class UserJpaEntity {
    @Id UUID id;
    @Column(nullable = false, unique = true, length = 32) String username;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(nullable = false, length = 16) String status;
    @Column(name = "security_version", nullable = false) long securityVersion;
    @Version @Column(name = "lock_version", nullable = false) Long lockVersion;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 16)
    Set<String> roles = new HashSet<>();
    protected UserJpaEntity() { }
}
