package com.llmhandoff.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or less")
        String name,

        @Size(max = 2000, message = "Description must be 2000 characters or less")
        String description
) {
}