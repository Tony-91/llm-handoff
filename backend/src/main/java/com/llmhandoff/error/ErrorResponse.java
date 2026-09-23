package com.llmhandoff.error;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String message,
        String details,
        LocalDateTime timestamp,
        List<String> errors
) {
    public ErrorResponse(String message, String details) {
        this(message, details, LocalDateTime.now(), List.of());
    }

    public ErrorResponse(String message, String details, List<String> errors) {
        this(message, details, LocalDateTime.now(), errors);
    }
}