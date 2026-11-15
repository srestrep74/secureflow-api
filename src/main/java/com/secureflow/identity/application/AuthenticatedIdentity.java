package com.secureflow.identity.application;

import com.secureflow.shared.domain.UserId;

public record AuthenticatedIdentity(UserId userId, String username) {}
