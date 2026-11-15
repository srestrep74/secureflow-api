package com.secureflow.identity.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

interface SpringUserRepository extends JpaRepository<UserJpaEntity, UUID> {
    @EntityGraph(attributePaths = "roles") Optional<UserJpaEntity> findWithRolesById(UUID id);
    @EntityGraph(attributePaths = "roles") Optional<UserJpaEntity> findByUsername(String username);
    @EntityGraph(attributePaths = "roles") Optional<UserJpaEntity> findByEmail(String email);
}
