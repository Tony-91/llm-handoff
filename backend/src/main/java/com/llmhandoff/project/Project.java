package com.llmhandoff.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(Types.BINARY)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2_000)
    private String description;

    @Column(name = "repo_identifier", length = 255)
    private String repoIdentifier;

    @Column(name = "default_branch", length = 100, nullable = false)
    private String defaultBranch = "main";

    @Column(name = "git_sync_status", length = 30, nullable = false)
    private String gitSyncStatus = "NOT_CONFIGURED";

    @Column(name = "git_sync_error", length = 1000)
    private String gitSyncError;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
        // Required by JPA.
    }

    public Project(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getRepoIdentifier() {
        return repoIdentifier;
    }

    public void setRepoIdentifier(String repoIdentifier) {
        this.repoIdentifier = repoIdentifier;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch != null && !defaultBranch.isBlank() ? defaultBranch : "main";
    }

    public String getGitSyncStatus() {
        return gitSyncStatus;
    }

    public void setGitSyncStatus(String gitSyncStatus) {
        this.gitSyncStatus = gitSyncStatus;
    }

    public String getGitSyncError() {
        return gitSyncError;
    }

    public void setGitSyncError(String gitSyncError) {
        this.gitSyncError = gitSyncError;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
