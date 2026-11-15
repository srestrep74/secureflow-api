package com.secureflow.identity.application;

import com.secureflow.identity.domain.*;
import com.secureflow.shared.domain.UserId;
import java.util.Optional;

public interface UserRepositoryPort {
    void create(User user);

    void update(User user, long expectedSecurityVersion);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(EmailAddress email);
}
