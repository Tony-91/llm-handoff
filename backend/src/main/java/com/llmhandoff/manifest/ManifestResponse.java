package com.llmhandoff.manifest;

import java.time.Instant;
import java.util.UUID;

public record ManifestResponse(
        UUID id,
        UUID projectId,
        String schemaVersion,
        ManifestData parsedData,
        String rawContent,
        Instant createdAt,
        Instant updatedAt
) {
    public static ManifestResponse from(Manifest manifest, ManifestData parsedData) {
        return new ManifestResponse(
                manifest.getId(),
                manifest.getProject().getId(),
                manifest.getSchemaVersion(),
                parsedData,
                manifest.getRawContent(),
                manifest.getCreatedAt(),
                manifest.getUpdatedAt()
        );
    }
}
