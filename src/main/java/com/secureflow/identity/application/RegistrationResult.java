package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;

public record RegistrationResult(UserId userId, String username, String email) {}
