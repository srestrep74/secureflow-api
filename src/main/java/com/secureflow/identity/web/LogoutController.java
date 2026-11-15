package com.secureflow.identity.web;

import com.secureflow.identity.application.CurrentLogoutContext;
import com.secureflow.identity.application.LogoutCurrentSessionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.security.SecurityScheme(
        name = "bearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class LogoutController {
    private final LogoutCurrentSessionUseCase logout;
    private final CurrentLogoutContext context;

    LogoutController(LogoutCurrentSessionUseCase logout, CurrentLogoutContext context) {
        this.logout = logout;
        this.context = context;
    }

    @Operation(
            summary = "Log out the current session",
            description =
                    "Revokes the current session family after Bearer authentication. Subsequent requests with its access or refresh tokens are rejected; other sessions remain active. Repeating logout with the revoked access token returns 401. Discard both locally stored tokens after logout.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Current session revoked")
    @ApiResponse(responseCode = "401", description = "Authentication or session rejected")
    @ApiResponse(responseCode = "503", description = "Service unavailable")
    @ApiResponse(responseCode = "500", description = "Internal error")
    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        logout.logout(context.current());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .build();
    }
}
