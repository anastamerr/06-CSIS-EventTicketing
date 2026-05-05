package com.team06.eventticketing.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public void handle(ResponseStatusException ex, HttpServletResponse response) throws IOException {
        writeError(response, ex.getStatusCode().value(), ex.getReason());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public void handleDataIntegrity(DataIntegrityViolationException ex, HttpServletResponse response) throws IOException {
        writeError(response, HttpStatus.CONFLICT.value(), "Unique constraint violation");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", message == null ? HttpStatus.valueOf(status).getReasonPhrase() : message);

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
