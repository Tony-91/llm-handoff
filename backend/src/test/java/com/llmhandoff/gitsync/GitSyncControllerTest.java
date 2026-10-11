package com.llmhandoff.gitsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.GlobalExceptionHandler;
import com.llmhandoff.manifest.ManifestData;
import com.llmhandoff.manifest.ManifestResponse;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectResponse;
import com.llmhandoff.project.UpdateGitConfigRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitSyncController.class)
@Import(GlobalExceptionHandler.class)
class GitSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GitSyncService gitSyncService;

    @MockitoBean
    private RepoInspectionService repoInspectionService;

    @Test
    @DisplayName("PUT /api/projects/{projectId}/git-config updates repository config")
    void updateGitConfig_success() throws Exception {
        UUID projectId = UUID.randomUUID();
        UpdateGitConfigRequest request = new UpdateGitConfigRequest("tonyortiz/my-app", "main");

        Project project = new Project("My App", "Test");
        project.setRepoIdentifier("tonyortiz/my-app");
        project.setDefaultBranch("main");
        project.setGitSyncStatus("ACTIVE");

        when(gitSyncService.updateGitConfig(eq(projectId), eq("tonyortiz/my-app"), eq("main"))).thenReturn(project);

        mockMvc.perform(put("/api/projects/{projectId}/git-config", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repoIdentifier").value("tonyortiz/my-app"))
                .andExpect(jsonPath("$.defaultBranch").value("main"))
                .andExpect(jsonPath("$.gitSyncStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/sync-git triggers sync and returns 200 OK")
    void syncProjectFromGit_success() throws Exception {
        UUID projectId = UUID.randomUUID();
        ManifestData parsed = new ManifestData("1.0", null, null, null, null);
        ManifestResponse response = new ManifestResponse(UUID.randomUUID(), projectId, "1.0", parsed, "{}", Instant.now(), Instant.now());

        when(gitSyncService.syncProjectFromGit(projectId)).thenReturn(response);

        mockMvc.perform(post("/api/projects/{projectId}/sync-git", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.schemaVersion").value("1.0"));
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/sync-git returns 400 when GitSyncException thrown")
    void syncProjectFromGit_error() throws Exception {
        UUID projectId = UUID.randomUUID();

        when(gitSyncService.syncProjectFromGit(projectId))
                .thenThrow(new GitSyncException("File not found on GitHub"));

        mockMvc.perform(post("/api/projects/{projectId}/sync-git", projectId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File not found on GitHub"));
    }

    @Test
    @DisplayName("POST /api/projects/{projectId}/generate-manifest inspects repo and returns candidate manifest")
    void generateManifestFromRepo_success() throws Exception {
        UUID projectId = UUID.randomUUID();
        ManifestData parsed = new ManifestData("1.0", null, null, null, null);
        GeneratedManifestResponse response = new GeneratedManifestResponse(
                "{\"schemaVersion\":\"1.0\"}",
                parsed,
                List.of("pom.xml"),
                Map.of("Language", "Java 21", "Framework", "Spring Boot 3.5.0")
        );

        when(repoInspectionService.generateManifestFromRepo(eq(projectId), any())).thenReturn(response);

        mockMvc.perform(post("/api/projects/{projectId}/generate-manifest", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawJson").value("{\"schemaVersion\":\"1.0\"}"))
                .andExpect(jsonPath("$.detectedFiles[0]").value("pom.xml"))
                .andExpect(jsonPath("$.summaryHighlights.Language").value("Java 21"));
    }

    @Test
    @DisplayName("POST /api/webhooks/github handles push payload")
    void handleGitHubWebhook_success() throws Exception {
        GitSyncService.MapPayload payload = new GitSyncService.MapPayload(
                new GitSyncService.WebhookRepository("tonyortiz/my-app"),
                "refs/heads/main"
        );

        UUID projectId = UUID.randomUUID();
        when(gitSyncService.handleGitHubWebhook(any(GitSyncService.MapPayload.class))).thenReturn(List.of(projectId));

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.syncedProjectCount").value(1));
    }
}
