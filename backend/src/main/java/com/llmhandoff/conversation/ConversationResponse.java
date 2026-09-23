package com.llmhandoff.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID projectId,
        String content,
        String title,
        String source,
        String processingStatus,
        String processingError,
        Instant createdAt,
        Instant updatedAt
) {
    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getProject().getId(),
                conversation.getContent(),
                conversation.getTitle(),
                conversation.getSource(),
                conversation.getProcessingStatus(),
                conversation.getProcessingError(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}