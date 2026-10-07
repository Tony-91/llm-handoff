package com.llmhandoff.manifest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ManifestRepository extends JpaRepository<Manifest, UUID> {
    Optional<Manifest> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Manifest> findByIdAndProjectId(UUID id, UUID projectId);
}
