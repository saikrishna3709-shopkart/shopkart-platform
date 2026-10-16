package com.shopkart.order.web;

import java.util.Map;

import com.shopkart.order.service.DependencyUnavailableException;
import com.shopkart.order.service.InvalidTransitionException;
import com.shopkart.order.service.OrderNotFoundException;
import com.shopkart.order.service.TooManyItemsException;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(OrderNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, String>> conflict(InvalidTransitionException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(DependencyUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler({
            TooManyItemsException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "request validation failed");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("detail", exception.getReason() == null ? "request failed" : exception.getReason()));
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(Map.of("detail", detail));
    }
}