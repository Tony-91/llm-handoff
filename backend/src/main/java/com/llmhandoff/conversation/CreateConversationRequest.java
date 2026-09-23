package com.llmhandoff.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 100000, message = "Content must be 100000 characters or less")
        String content,

        @Size(max = 200, message = "Title must be 200 characters or less")
        String title,

        @Size(max = 50, message = "Source must be 50 characters or less")
        String source
) {
    public CreateConversationRequest(String content) {
        this(content, null, null);
    }
}