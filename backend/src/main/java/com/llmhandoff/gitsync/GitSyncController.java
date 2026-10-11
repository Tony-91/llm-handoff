package com.llmhandoff.gitsync;

import com.llmhandoff.manifest.ManifestResponse;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectResponse;
import com.llmhandoff.project.UpdateGitConfigRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GitSyncController {

    private final GitSyncService gitSyncService;
    private final RepoInspectionService repoInspectionService;

    public GitSyncController(GitSyncService gitSyncService, RepoInspectionService repoInspectionService) {
        this.gitSyncService = gitSyncService;
        this.repoInspectionService = repoInspectionService;
    }

    /**
     * Configure GitHub repo settings for a project.
     */
    @PutMapping("/projects/{projectId}/git-config")
    public ResponseEntity<ProjectResponse> updateGitConfig(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateGitConfigRequest request
    ) {
        Project updated = gitSyncService.updateGitConfig(projectId, request.repoIdentifier(), request.defaultBranch());
        return ResponseEntity.ok(ProjectResponse.from(updated));
    }

    /**
     * Trigger manual on-demand sync from GitHub.
     */
    @PostMapping("/projects/{projectId}/sync-git")
    public ResponseEntity<ManifestResponse> syncProjectFromGit(@PathVariable UUID projectId) {
        ManifestResponse response = gitSyncService.syncProjectFromGit(projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * Auto-detect project descriptors in repository and generate candidate .llmhandoff.json draft.
     */
    @PostMapping("/projects/{projectId}/generate-manifest")
    public ResponseEntity<GeneratedManifestResponse> generateManifestFromRepo(
            @PathVariable UUID projectId,
            @RequestBody(required = false) GenerateManifestRequest request
    ) {
        GeneratedManifestResponse response = repoInspectionService.generateManifestFromRepo(projectId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GitHub webhook endpoint to handle push events automatically.
     */
    @PostMapping("/webhooks/github")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(@RequestBody GitSyncService.MapPayload payload) {
        List<UUID> syncedProjects = gitSyncService.handleGitHubWebhook(payload);
        return ResponseEntity.ok(Map.of(
                "status", "PROCESSED",
                "syncedProjectCount", syncedProjects.size(),
                "syncedProjectIds", syncedProjects
        ));
    }
}
