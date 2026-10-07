package com.llmhandoff.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.GlobalExceptionHandler;
import com.llmhandoff.error.ManifestNotFoundException;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManifestController.class)
@Import(GlobalExceptionHandler.class)
class ManifestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ManifestService manifestService;

    private UUID projectId;
    private String validManifestJson;
    private ManifestResponse sampleResponse;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        validManifestJson = """
                {
                  "schemaVersion": "1.0",
                  "project": { "name": "Test Project", "description": "Test Desc" },
                  "environment": { "language": "Java 21" },
                  "commands": { "build": "mvn compile" }
                }
                """;

        ManifestData data = new ManifestData(
                "1.0",
                new ManifestData.ProjectInfo("Test Project", "Test Desc"),
                new ManifestData.EnvironmentInfo("Java 21", "Spring Boot", "MySQL", "Maven", null),
                Map.of("build", "mvn compile"),
                null
        );

        sampleResponse = new ManifestResponse(
                UUID.randomUUID(),
                projectId,
                "1.0",
                data,
                validManifestJson,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void saveManifest_returnsCreatedStatus() throws Exception {
        SaveManifestRequest request = new SaveManifestRequest(validManifestJson);

        when(manifestService.saveManifest(eq(projectId), any(SaveManifestRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/projects/{projectId}/manifest", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.parsedData.environment.language").value("Java 21"));
    }

    @Test
    void saveManifest_whenBlankContent_returnsBadRequest() throws Exception {
        SaveManifestRequest request = new SaveManifestRequest("");

        mockMvc.perform(post("/api/projects/{projectId}/manifest", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void saveManifest_whenProjectNotFound_returnsNotFound() throws Exception {
        SaveManifestRequest request = new SaveManifestRequest(validManifestJson);

        when(manifestService.saveManifest(eq(projectId), any(SaveManifestRequest.class)))
                .thenThrow(new ProjectNotFoundException("Project not found with id: " + projectId));

        mockMvc.perform(post("/api/projects/{projectId}/manifest", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found with id: " + projectId));
    }

    @Test
    void getLatestManifest_returnsOk() throws Exception {
        when(manifestService.getLatestManifest(projectId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/projects/{projectId}/manifest", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.parsedData.project.name").value("Test Project"));
    }

    @Test
    void getLatestManifest_whenNotFound_returnsNotFound() throws Exception {
        when(manifestService.getLatestManifest(projectId))
                .thenThrow(new ManifestNotFoundException("No manifest found for project: " + projectId));

        mockMvc.perform(get("/api/projects/{projectId}/manifest", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No manifest found for project: " + projectId));
    }

    @Test
    void getHandoffPackage_returnsOk() throws Exception {
        HandoffPackageResponse packageResponse = new HandoffPackageResponse(
                projectId,
                "Test Project",
                "# Project Context: Test Project\n- Language: Java 21",
                Instant.now()
        );

        when(manifestService.generateHandoffPackage(projectId)).thenReturn(packageResponse);

        mockMvc.perform(get("/api/projects/{projectId}/handoff-package", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andExpect(jsonPath("$.markdownPrompt").value(org.hamcrest.Matchers.containsString("Java 21")));
    }
}
