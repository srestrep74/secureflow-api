package com.secureflow.identity.web;

import com.secureflow.identity.application.LoginUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "secureflow.jwt", name = "enabled", havingValue = "true")
class LoginController {
    private final LoginUseCase login;
    private final LoginMapper mapper = new LoginMapper();

    LoginController(LoginUseCase login) {
        this.login = login;
    }

    @Operation(
            summary = "Authenticate credentials",
            description =
                    "Public credential exchange over HTTPS. Access JWT authentication for protected routes is not yet enabled.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Bearer access JWT and opaque refresh token")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "500", description = "Internal error")
    @ApiResponse(responseCode = "503", description = "Service unavailable")
    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var response = mapper.toResponse(login.login(mapper.toCommand(request)));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }
}
