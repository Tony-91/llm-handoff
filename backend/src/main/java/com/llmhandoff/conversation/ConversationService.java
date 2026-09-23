package com.llmhandoff.conversation;

import com.llmhandoff.error.ConversationNotFoundException;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.util.List;
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
        
        Conversation conversation = new Conversation(project, request.content(), request.title(), request.source());
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<Conversation> getConversationsByProjectId(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException("Project not found with id: " + projectId);
        }
        return conversationRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Conversation getConversationByIdAndProjectId(UUID conversationId, UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException("Project not found with id: " + projectId);
        }
        return conversationRepository.findByIdAndProjectId(conversationId, projectId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found with id: " + conversationId));
    }
}