package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.function.Supplier;

public interface LoginTransactionPort {
    LoginResult execute(UserId userId, Supplier<LoginResult> operation);
}
