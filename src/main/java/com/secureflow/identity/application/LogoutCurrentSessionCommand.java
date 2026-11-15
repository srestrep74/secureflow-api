package com.secureflow.identity.application;

import java.util.UUID;

public record LogoutCurrentSessionCommand(UUID userId, UUID familyId) {}
