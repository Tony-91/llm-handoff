package com.llmhandoff.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String repoIdentifier,
        String defaultBranch,
        String gitSyncStatus,
        String gitSyncError,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getRepoIdentifier(),
                project.getDefaultBranch(),
                project.getGitSyncStatus(),
                project.getGitSyncError(),
                project.getLastSyncedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}