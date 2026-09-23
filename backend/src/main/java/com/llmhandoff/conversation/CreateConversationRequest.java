package com.llmhandoff.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 100000, message = "Content must be 100000 characters or less")
        String content
) {
}