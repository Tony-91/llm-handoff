package com.llmhandoff.manifest;

import jakarta.validation.constraints.NotBlank;

public record SaveManifestRequest(
        @NotBlank(message = "Manifest content cannot be blank")
        String content
) {}
