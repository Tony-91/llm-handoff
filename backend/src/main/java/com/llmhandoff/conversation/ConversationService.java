package com.llmhandoff.conversation;

import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;

    public ConversationService(ConversationRepository conversationRepository, ProjectRepository projectRepository) {
        this.conversationRepository = conversationRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Conversation createConversation(UUID projectId, CreateConversationRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
        
        Conversation conversation = new Conversation(project, request.content());
        return conversationRepository.save(conversation);
    }
}