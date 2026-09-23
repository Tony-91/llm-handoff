package com.llmhandoff.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.ConversationNotFoundException;
import com.llmhandoff.error.GlobalExceptionHandler;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConversationController.class)
@Import(GlobalExceptionHandler.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConversationService conversationService;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test Project", "Test Description");
    }

    @Test
    void createConversation_returnsCreatedStatus() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("Pasted conversation text", "My Chat", "ChatGPT");
        Conversation conversation = new Conversation(project, request.content(), request.title(), request.source());

        when(conversationService.createConversation(eq(projectId), any(CreateConversationRequest.class)))
                .thenReturn(conversation);

        mockMvc.perform(post("/api/projects/{projectId}/conversations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Pasted conversation text"))
                .andExpect(jsonPath("$.title").value("My Chat"))
                .andExpect(jsonPath("$.source").value("ChatGPT"))
                .andExpect(jsonPath("$.processingStatus").value("PENDING"));
    }

    @Test
    void createConversation_whenBlankContent_returnsBadRequest() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("");

        mockMvc.perform(post("/api/projects/{projectId}/conversations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void createConversation_whenProjectNotFound_returnsNotFound() throws Exception {
        CreateConversationRequest request = new CreateConversationRequest("Some content");

        when(conversationService.createConversation(eq(projectId), any(CreateConversationRequest.class)))
                .thenThrow(new ProjectNotFoundException("Project not found with id: " + projectId));

        mockMvc.perform(post("/api/projects/{projectId}/conversations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found with id: " + projectId));
    }

    @Test
    void getConversationsByProjectId_returnsList() throws Exception {
        Conversation c1 = new Conversation(project, "Content 1", "Title 1", "ChatGPT");
        Conversation c2 = new Conversation(project, "Content 2", "Title 2", "Claude");

        when(conversationService.getConversationsByProjectId(projectId)).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/projects/{projectId}/conversations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Title 1"))
                .andExpect(jsonPath("$[1].title").value("Title 2"));
    }

    @Test
    void getConversationById_returnsConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation(project, "Content 1", "Title 1", "ChatGPT");

        when(conversationService.getConversationByIdAndProjectId(conversationId, projectId)).thenReturn(conversation);

        mockMvc.perform(get("/api/projects/{projectId}/conversations/{conversationId}", projectId, conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Title 1"))
                .andExpect(jsonPath("$.content").value("Content 1"));
    }

    @Test
    void getConversationById_whenNotFound_returnsNotFound() throws Exception {
        UUID conversationId = UUID.randomUUID();

        when(conversationService.getConversationByIdAndProjectId(conversationId, projectId))
                .thenThrow(new ConversationNotFoundException("Conversation not found with id: " + conversationId));

        mockMvc.perform(get("/api/projects/{projectId}/conversations/{conversationId}", projectId, conversationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Conversation not found with id: " + conversationId));
    }
}
