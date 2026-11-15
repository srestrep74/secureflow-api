package com.secureflow.identity.infrastructure;

import com.secureflow.identity.application.UserRepositoryPort;
import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import com.secureflow.shared.application.ConcurrentUpdateException;
import com.secureflow.shared.application.IntegrityConflictException;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {
    private final SpringUserRepository repository;
    private final UserPersistenceMapper mapper = new UserPersistenceMapper();
    UserPersistenceAdapter(SpringUserRepository repository) { this.repository = repository; }
    @Override @Transactional public void create(User user) {
        try { repository.saveAndFlush(mapper.toEntity(user)); }
        catch (DataIntegrityViolationException e) { throw new IntegrityConflictException("User integrity conflict", e); }
    }
    @Override @Transactional public void update(User user, long expectedSecurityVersion) {
        var entity = repository.findWithRolesById(user.id().value()).orElseThrow();
        if (entity.securityVersion != expectedSecurityVersion || user.securityVersion() < expectedSecurityVersion)
            throw new ConcurrentUpdateException("Stale user");
        if (!entity.username.equals(user.username().value()) || !entity.email.equals(user.email().value()))
            throw new IllegalArgumentException("Identity cannot change");
        if (user.securityVersion() == expectedSecurityVersion && (!entity.status.equals(user.status().name())
                || !entity.roles.equals(user.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()))))
            throw new IllegalArgumentException("User changed without advancing its security version");
        mapper.copy(user, entity);
        try { repository.flush(); }
        catch (OptimisticLockingFailureException e) { throw new ConcurrentUpdateException("Stale user", e); }
        catch (DataIntegrityViolationException e) { throw new IntegrityConflictException("User integrity conflict", e); }
    }
    @Override @Transactional(readOnly = true) public Optional<User> findById(UserId id) { return repository.findWithRolesById(id.value()).map(mapper::toDomain); }
    @Override @Transactional(readOnly = true) public Optional<User> findByUsername(Username username) { return repository.findByUsername(username.value()).map(mapper::toDomain); }
    @Override @Transactional(readOnly = true) public Optional<User> findByEmail(EmailAddress email) { return repository.findByEmail(email.value()).map(mapper::toDomain); }
}
