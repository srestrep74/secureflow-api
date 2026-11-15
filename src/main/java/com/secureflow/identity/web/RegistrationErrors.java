package com.secureflow.identity.web;

import com.secureflow.shared.application.IntegrityConflictException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RegistrationErrors {
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(Exception exception) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail invalid(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid registration request");
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

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ProblemDetail unavailable(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
