package com.llmhandoff.manifest;

import java.time.Instant;
import java.util.UUID;

public record HandoffPackageResponse(
        UUID projectId,
        String projectName,
        String markdownPrompt,
        Instant generatedAt
) {}
