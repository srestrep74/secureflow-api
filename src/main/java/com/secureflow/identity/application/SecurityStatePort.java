package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;
import java.util.UUID;

public interface SecurityStatePort {
    SecurityStateSnapshot findCurrentState(UserId userId, UUID sessionFamilyId);
}
