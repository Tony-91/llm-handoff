package com.llmhandoff.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByRepoIdentifier(String repoIdentifier);
    List<Project> findByUserId(UUID userId);
    Optional<Project> findByIdAndUserId(UUID id, UUID userId);
}