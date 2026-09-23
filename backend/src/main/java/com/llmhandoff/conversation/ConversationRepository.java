package com.llmhandoff.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByProjectId(UUID projectId);
    Optional<Conversation> findByIdAndProjectId(UUID id, UUID projectId);
}