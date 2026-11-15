package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.UUID;
import java.util.function.BiFunction;

public interface RefreshTransactionPort {
    RefreshOutcome execute(String digest, BiFunction<UserId, UUID, RefreshOutcome> operation);
}
