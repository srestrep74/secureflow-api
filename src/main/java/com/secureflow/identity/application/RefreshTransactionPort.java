package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import java.util.function.BiFunction;

public interface RefreshTransactionPort {
    boolean executeForSession(UserId userId, UUID familyId, java.util.function.BooleanSupplier operation);

    RefreshOutcome execute(String digest, BiFunction<UserId, UUID, RefreshOutcome> operation);
}
