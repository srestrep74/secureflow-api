package com.secureflow.identity.infrastructure;

import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import java.util.stream.Collectors;

final class UserPersistenceMapper {
    UserJpaEntity toEntity(User user) {
        var entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.username = user.username().value();
        entity.email = user.email().value();
        copy(user, entity);
        return entity;
    }

    void copy(User user, UserJpaEntity entity) {
        entity.status = user.status().name();
        entity.securityVersion = user.securityVersion();
        var roles = user.roles().stream().map(Enum::name).collect(Collectors.toSet());
        if (!entity.roles.equals(roles)) {
            entity.roles.clear();
            entity.roles.addAll(roles);
        }
    }

    User toDomain(UserJpaEntity entity) {
        return User.reconstitute(
                new UserId(entity.id),
                new Username(entity.username),
                new EmailAddress(entity.email),
                entity.roles.stream().map(Role::valueOf).collect(Collectors.toSet()),
                AccountStatus.valueOf(entity.status),
                entity.securityVersion);
    }
}
