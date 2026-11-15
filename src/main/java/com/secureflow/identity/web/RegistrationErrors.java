package com.secureflow.identity.web;

import com.secureflow.identity.application.AuthenticationUnavailableException;
import com.secureflow.identity.application.InvalidCredentialsException;
import com.secureflow.identity.application.InvalidLogoutContextException;
import com.secureflow.identity.application.InvalidRefreshTokenException;
import com.secureflow.identity.application.LogoutUnavailableException;
import com.secureflow.identity.application.RefreshReplayException;
import com.secureflow.identity.application.RefreshUnavailableException;
import com.secureflow.shared.application.IntegrityConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RegistrationErrors {
    @ExceptionHandler({InvalidRefreshTokenException.class, RefreshReplayException.class})
    ProblemDetail invalidRefresh(Exception exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Refresh credential rejected; authenticate again");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(Exception exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMedia(Exception exception) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(Exception exception) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail invalid(Exception exception, HttpServletRequest request) {
        if (exception instanceof IllegalArgumentException
                && request.getRequestURI().equals(request.getContextPath() + "/api/v1/auth/login")) {
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
        return problem(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler(IntegrityConflictException.class)
    ProblemDetail integrity(IntegrityConflictException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && ("users_username_key".equals(constraint.getConstraintName())
                            || "users_email_key".equals(constraint.getConstraintName()))) {
                return problem(HttpStatus.CONFLICT, "Username or email already exists");
            }
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, AuthenticationUnavailableException.class})
    ProblemDetail unavailable(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
    }

    @ExceptionHandler(InvalidLogoutContextException.class)
    org.springframework.http.ResponseEntity<ProblemDetail> invalidLogout(InvalidLogoutContextException exception) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, "Authentication rejected"));
    }

    @ExceptionHandler(LogoutUnavailableException.class)
    ProblemDetail logoutFailure(LogoutUnavailableException exception) {
        return classifiedFailure(exception);
    }

    @ExceptionHandler(RefreshUnavailableException.class)
    ProblemDetail refreshFailure(RefreshUnavailableException exception) {
        return classifiedFailure(exception);
    }

    private ProblemDetail classifiedFailure(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof com.secureflow.identity.application.SecurityStateUnavailableException
                    || cause instanceof org.springframework.transaction.CannotCreateTransactionException) {
                return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
            }
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
