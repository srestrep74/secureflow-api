package com.secureflow.identity.web;

import java.util.UUID;

public record RegistrationResponse(UUID id, String username, String email) {}
