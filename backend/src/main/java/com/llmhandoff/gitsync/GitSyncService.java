package com.llmhandoff.gitsync;

import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.manifest.ManifestResponse;
import com.llmhandoff.manifest.ManifestService;
import com.llmhandoff.manifest.SaveManifestRequest;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitSyncService.class);

    private final ProjectRepository projectRepository;
    private final ManifestService manifestService;
    private final GitHubClient gitHubClient;

    public GitSyncService(
            ProjectRepository projectRepository,
            ManifestService manifestService,
            GitHubClient gitHubClient
    ) {
        this.projectRepository = projectRepository;
        this.manifestService = manifestService;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Updates git configuration for a project.
     */
    @Transactional
    public Project updateGitConfig(UUID projectId, String repoIdentifier, String branch) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        if (repoIdentifier == null || repoIdentifier.isBlank()) {
            project.setRepoIdentifier(null);
            project.setGitSyncStatus("NOT_CONFIGURED");
            project.setGitSyncError(null);
        } else {
            String normalized = gitHubClient.normalizeRepoIdentifier(repoIdentifier);
            project.setRepoIdentifier(normalized);
            project.setDefaultBranch(branch != null && !branch.isBlank() ? branch.trim() : "main");
            project.setGitSyncStatus("ACTIVE");
            project.setGitSyncError(null);
        }

        return projectRepository.save(project);
    }

    /**
     * Synchronizes a single project with its configured GitHub repository.
     */
    @Transactional
    public ManifestResponse syncProjectFromGit(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        if (project.getRepoIdentifier() == null || project.getRepoIdentifier().isBlank()) {
            throw new GitSyncException("Project does not have a GitHub repository configured");
        }

        try {
            log.info("Starting git sync for project {} from {}", projectId, project.getRepoIdentifier());
            String rawJson = gitHubClient.fetchManifestContent(project.getRepoIdentifier(), project.getDefaultBranch());
            ManifestResponse savedManifest = manifestService.saveManifest(projectId, new SaveManifestRequest(rawJson));

            project.setGitSyncStatus("SYNCED");
            project.setGitSyncError(null);
            project.setLastSyncedAt(Instant.now());
            projectRepository.save(project);

            log.info("Successfully synced project {} from {}", projectId, project.getRepoIdentifier());
            return savedManifest;
        } catch (Exception e) {
            project.setGitSyncStatus("FAILED");
            project.setGitSyncError(e.getMessage());
            projectRepository.save(project);
            log.error("Failed to sync project {} from {}: {}", projectId, project.getRepoIdentifier(), e.getMessage());
            throw new GitSyncException("Git sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles incoming GitHub webhook push payload and syncs matching projects.
     */
    @Transactional
    public List<UUID> handleGitHubWebhook(MapPayload payload) {
        if (payload == null || payload.repository() == null || payload.repository().fullName() == null) {
            log.warn("Ignoring invalid GitHub webhook payload");
            return List.of();
        }

        String repoFullName = payload.repository().fullName().trim();
        List<Project> matchingProjects = projectRepository.findByRepoIdentifier(repoFullName);

        if (matchingProjects.isEmpty()) {
            log.info("Received GitHub webhook for unlinked repository: {}", repoFullName);
            return List.of();
        }

        List<UUID> syncedProjectIds = new ArrayList<>();
        for (Project project : matchingProjects) {
            try {
                syncProjectFromGit(project.getId());
                syncedProjectIds.add(project.getId());
            } catch (Exception e) {
                log.error("Webhook triggered sync failed for project {}: {}", project.getId(), e.getMessage());
            }
        }

        return syncedProjectIds;
    }

    public record WebhookRepository(String full_name) {
        public String fullName() {
            return full_name;
        }
    }

    public record MapPayload(WebhookRepository repository, String ref) {
    }
}
