package com.llmhandoff.conversation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateConversationRequest request) {
        Conversation conversation = conversationService.createConversation(projectId, request);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/conversations/" + conversation.getId()))
                .body(ConversationResponse.from(conversation));
    }
}