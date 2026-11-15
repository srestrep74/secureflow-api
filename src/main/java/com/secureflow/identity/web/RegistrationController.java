package com.secureflow.identity.web;

import com.secureflow.identity.application.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class RegistrationController {
    private final RegisterUserUseCase register;
    private final RegistrationMapper mapper = new RegistrationMapper();

    RegistrationController(RegisterUserUseCase register) {
        this.register = register;
    }

    @Operation(summary = "Register a user", description = "Public endpoint; assigns the USER role on the server.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "User registered")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid registration request",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Username or email already exists",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return mapper.toResponse(register.register(mapper.toCommand(request)));
    }
}
