package com.llmhandoff.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGitConfigRequest(
        @NotBlank(message = "Repository identifier is required")
        @Size(max = 255, message = "Repository identifier must not exceed 255 characters")
        String repoIdentifier,

        @Size(max = 100, message = "Branch name must not exceed 100 characters")
        String defaultBranch
) {
}
