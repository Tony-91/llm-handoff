package com.llmhandoff.gitsync;

import com.llmhandoff.manifest.ManifestData;
import com.llmhandoff.manifest.ManifestResponse;
import com.llmhandoff.manifest.ManifestService;
import com.llmhandoff.manifest.SaveManifestRequest;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitSyncServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ManifestService manifestService;

    @Mock
    private GitHubClient gitHubClient;

    @InjectMocks
    private GitSyncService gitSyncService;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test App", "Description");
    }

    @Test
    @DisplayName("updateGitConfig sets normalized repository and branch")
    void updateGitConfig_success() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(gitHubClient.normalizeRepoIdentifier("https://github.com/my-org/my-repo")).thenReturn("my-org/my-repo");
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        Project updated = gitSyncService.updateGitConfig(projectId, "https://github.com/my-org/my-repo", "develop");

        assertEquals("my-org/my-repo", updated.getRepoIdentifier());
        assertEquals("develop", updated.getDefaultBranch());
        assertEquals("ACTIVE", updated.getGitSyncStatus());
    }

    @Test
    @DisplayName("updateGitConfig clears repository when blank")
    void updateGitConfig_clearRepo() {
        project.setRepoIdentifier("my-org/my-repo");
        project.setGitSyncStatus("SYNCED");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        Project updated = gitSyncService.updateGitConfig(projectId, "", "main");

        assertNull(updated.getRepoIdentifier());
        assertEquals("NOT_CONFIGURED", updated.getGitSyncStatus());
    }

    @Test
    @DisplayName("syncProjectFromGit fetches manifest from GitHub, saves it, and marks status SYNCED")
    void syncProjectFromGit_success() {
        project.setRepoIdentifier("my-org/my-repo");
        project.setDefaultBranch("main");

        String rawJson = "{\"schemaVersion\":\"1.0\"}";
        ManifestData parsed = new ManifestData("1.0", null, null, null, null);
        ManifestResponse response = new ManifestResponse(UUID.randomUUID(), projectId, "1.0", parsed, rawJson, Instant.now(), Instant.now());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(gitHubClient.fetchManifestContent("my-org/my-repo", "main")).thenReturn(rawJson);
        when(manifestService.saveManifest(eq(projectId), any(SaveManifestRequest.class))).thenReturn(response);
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        ManifestResponse result = gitSyncService.syncProjectFromGit(projectId);

        assertNotNull(result);
        assertEquals("SYNCED", project.getGitSyncStatus());
        assertNotNull(project.getLastSyncedAt());
        assertNull(project.getGitSyncError());
        verify(manifestService).saveManifest(eq(projectId), any(SaveManifestRequest.class));
    }

    @Test
    @DisplayName("syncProjectFromGit records FAILED status when git fetch throws exception")
    void syncProjectFromGit_failure() {
        project.setRepoIdentifier("my-org/my-repo");
        project.setDefaultBranch("main");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(gitHubClient.fetchManifestContent("my-org/my-repo", "main")).thenThrow(new GitSyncException("File not found on branch"));

        GitSyncException ex = assertThrows(GitSyncException.class, () -> gitSyncService.syncProjectFromGit(projectId));

        assertEquals("Git sync failed: File not found on branch", ex.getMessage());
        assertEquals("FAILED", project.getGitSyncStatus());
        assertEquals("File not found on branch", project.getGitSyncError());
        verify(projectRepository).save(project);
    }

    @Test
    @DisplayName("handleGitHubWebhook triggers sync for matching projects")
    void handleGitHubWebhook_success() {
        project.setRepoIdentifier("my-org/my-repo");
        project.setDefaultBranch("main");

        String rawJson = "{\"schemaVersion\":\"1.0\"}";
        ManifestData parsed = new ManifestData("1.0", null, null, null, null);
        ManifestResponse response = new ManifestResponse(UUID.randomUUID(), projectId, "1.0", parsed, rawJson, Instant.now(), Instant.now());

        when(projectRepository.findByRepoIdentifier("my-org/my-repo")).thenReturn(List.of(project));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(gitHubClient.fetchManifestContent("my-org/my-repo", "main")).thenReturn(rawJson);
        when(manifestService.saveManifest(eq(projectId), any(SaveManifestRequest.class))).thenReturn(response);

        GitSyncService.MapPayload payload = new GitSyncService.MapPayload(
                new GitSyncService.WebhookRepository("my-org/my-repo"),
                "refs/heads/main"
        );

        List<UUID> synced = gitSyncService.handleGitHubWebhook(payload);

        assertEquals(1, synced.size());
        assertEquals(projectId, synced.get(0));
    }
}
