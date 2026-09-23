package com.llmhandoff.conversation;

import com.llmhandoff.error.ConversationNotFoundException;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ConversationService conversationService;

    private Project project;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test Project", "Test Description");
    }

    @Test
    void createConversation_whenProjectExists_savesAndReturnsConversation() {
        CreateConversationRequest request = new CreateConversationRequest("Some conversation content", "Chat 1", "ChatGPT");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation saved = conversationService.createConversation(projectId, request);

        assertNotNull(saved);
        assertEquals("Some conversation content", saved.getContent());
        assertEquals("Chat 1", saved.getTitle());
        assertEquals("ChatGPT", saved.getSource());
        assertEquals("PENDING", saved.getProcessingStatus());
        assertEquals(project, saved.getProject());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createConversation_whenProjectDoesNotExist_throwsProjectNotFoundException() {
        CreateConversationRequest request = new CreateConversationRequest("Some conversation content");
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> conversationService.createConversation(projectId, request));
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getConversationsByProjectId_whenProjectExists_returnsList() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        Conversation c1 = new Conversation(project, "Content 1", "Title 1", "Claude");
        when(conversationRepository.findByProjectId(projectId)).thenReturn(List.of(c1));

        List<Conversation> result = conversationService.getConversationsByProjectId(projectId);

        assertEquals(1, result.size());
        assertEquals("Title 1", result.get(0).getTitle());
    }

    @Test
    void getConversationsByProjectId_whenProjectDoesNotExist_throwsProjectNotFoundException() {
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(ProjectNotFoundException.class, () -> conversationService.getConversationsByProjectId(projectId));
    }

    @Test
    void getConversationByIdAndProjectId_whenFound_returnsConversation() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation(project, "Content 1", "Title 1", "Claude");
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(conversationRepository.findByIdAndProjectId(conversationId, projectId)).thenReturn(Optional.of(conversation));

        Conversation result = conversationService.getConversationByIdAndProjectId(conversationId, projectId);

        assertNotNull(result);
        assertEquals("Content 1", result.getContent());
    }

    @Test
    void getConversationByIdAndProjectId_whenConversationNotFound_throwsConversationNotFoundException() {
        UUID conversationId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(conversationRepository.findByIdAndProjectId(conversationId, projectId)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class, () -> conversationService.getConversationByIdAndProjectId(conversationId, projectId));
    }
}
