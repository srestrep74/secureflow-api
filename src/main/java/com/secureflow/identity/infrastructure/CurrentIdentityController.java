package com.secureflow.identity.infrastructure;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CurrentIdentityController {
    @GetMapping("/api/v1/me")
    Identity me(Authentication authentication) {
        return new Identity(authentication.getName());
    }

    record Identity(String id) {}
}
