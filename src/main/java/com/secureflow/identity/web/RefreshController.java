package com.secureflow.identity.web;

import com.secureflow.identity.application.RefreshSessionCommand;
import com.secureflow.identity.application.RefreshSessionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class RefreshController {
    private final RefreshSessionUseCase refresh;

    RefreshController(RefreshSessionUseCase refresh) {
        this.refresh = refresh;
    }

    @Operation(
            summary = "Renew tokens",
            description =
                    "Use the single-use refresh credential over HTTPS without an access JWT. Each rotation keeps the original family expiration. Reuse revokes the family; authenticate again after rejection. Omit the Authorization header, as an expired Bearer token is rejected by the resource server.")
    @SecurityRequirements
    @ApiResponse(
            responseCode = "200",
            description = "New Bearer access JWT and opaque refresh token; access lifetime is 300 seconds")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Refresh credential rejected; authenticate again")
    @ApiResponse(responseCode = "415", description = "Unsupported media type")
    @ApiResponse(responseCode = "500", description = "Internal error")
    @ApiResponse(responseCode = "503", description = "Service unavailable")
    @PostMapping(value = "/refresh", consumes = "application/json", produces = "application/json")
    ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        var result = refresh.refresh(new RefreshSessionCommand(request.refreshToken()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new LoginResponse(
                        result.accessToken(), result.refreshToken(), result.tokenType(), result.expiresIn()));
    }
}
