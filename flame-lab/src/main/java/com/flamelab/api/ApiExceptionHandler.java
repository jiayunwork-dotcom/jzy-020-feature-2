package com.flamelab.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.flamelab.job.ErrorInfo;
import com.flamelab.job.ErrorType;
import com.flamelab.job.InputValidationException;
import com.flamelab.job.JobNotFoundException;

/** Maps exceptions to typed JSON error bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<ErrorResponse> validation(InputValidationException e) {
        return respond(HttpStatus.BAD_REQUEST, e.type(), e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(JobNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, ErrorType.JOB_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorType.MALFORMED_REQUEST,
                "request body is missing or not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> badPathVariable(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorType.MALFORMED_REQUEST,
                "job id must be an integer, got '" + e.getValue() + "'");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("unexpected error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_ERROR, "internal error");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorType type, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(new ErrorInfo(type, message)));
    }
}
