package com.llmhandoff.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.ManifestNotFoundException;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManifestService {

    private final ManifestRepository manifestRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public ManifestService(
            ManifestRepository manifestRepository,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper
    ) {
        this.manifestRepository = manifestRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public ManifestResponse saveManifest(UUID projectId, SaveManifestRequest request, UUID userId) {
        Project project = getProjectWithOwnershipCheck(projectId, userId);

        ManifestData manifestData = parseManifestData(request.content());
        String schemaVersion = manifestData.schemaVersion() != null ? manifestData.schemaVersion() : "1.0";

        Manifest manifest = new Manifest(project, schemaVersion, request.content());
        Manifest saved = manifestRepository.save(manifest);

        return ManifestResponse.from(saved, manifestData);
    }

    public ManifestResponse saveManifest(UUID projectId, SaveManifestRequest request) {
        return saveManifest(projectId, request, null);
    }

    @Transactional(readOnly = true)
    public ManifestResponse getLatestManifest(UUID projectId, UUID userId) {
        getProjectWithOwnershipCheck(projectId, userId);

        Manifest manifest = manifestRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ManifestNotFoundException("No manifest found for project: " + projectId));

        ManifestData manifestData = parseManifestData(manifest.getRawContent());
        return ManifestResponse.from(manifest, manifestData);
    }

    @Transactional(readOnly = true)
    public ManifestResponse getLatestManifest(UUID projectId) {
        return getLatestManifest(projectId, null);
    }

    @Transactional(readOnly = true)
    public HandoffPackageResponse generateHandoffPackage(UUID projectId, UUID userId) {
        Project project = getProjectWithOwnershipCheck(projectId, userId);

        Manifest manifest = manifestRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ManifestNotFoundException("No manifest found for project: " + projectId));

        ManifestData manifestData = parseManifestData(manifest.getRawContent());
        String markdown = renderMarkdownPrompt(project, manifestData);

        return new HandoffPackageResponse(
                project.getId(),
                project.getName(),
                markdown,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public HandoffPackageResponse generateHandoffPackage(UUID projectId) {
        return generateHandoffPackage(projectId, null);
    }

    private Project getProjectWithOwnershipCheck(UUID projectId, UUID userId) {
        if (userId != null) {
            return projectRepository.findByIdAndUserId(projectId, userId)
                    .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
    }

    private ManifestData parseManifestData(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, ManifestData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid manifest JSON: " + e.getMessage(), e);
        }
    }

    private String renderMarkdownPrompt(Project project, ManifestData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project Context: ").append(project.getName()).append("\n\n");

        if (data.project() != null) {
            sb.append("## Overview\n");
            if (data.project().description() != null) {
                sb.append(data.project().description()).append("\n\n");
            }
        } else if (project.getDescription() != null) {
            sb.append("## Overview\n").append(project.getDescription()).append("\n\n");
        }

        if (data.environment() != null) {
            sb.append("## Tech Stack & Environment\n");
            if (data.environment().language() != null) sb.append("- **Language**: ").append(data.environment().language()).append("\n");
            if (data.environment().framework() != null) sb.append("- **Framework**: ").append(data.environment().framework()).append("\n");
            if (data.environment().database() != null) sb.append("- **Database**: ").append(data.environment().database()).append("\n");
            if (data.environment().buildTool() != null) sb.append("- **Build Tool**: ").append(data.environment().buildTool()).append("\n");
            if (data.environment().runtime() != null) sb.append("- **Runtime**: ").append(data.environment().runtime()).append("\n");
            sb.append("\n");
        }

        if (data.commands() != null && !data.commands().isEmpty()) {
            sb.append("## Setup & Commands\n");
            for (Map.Entry<String, String> entry : data.commands().entrySet()) {
                sb.append("- **").append(entry.getKey()).append("**: `").append(entry.getValue()).append("`\n");
            }
            sb.append("\n");
        }

        if (data.context() != null) {
            if (data.context().architecture() != null && !data.context().architecture().isBlank()) {
                sb.append("## Architecture\n").append(data.context().architecture()).append("\n\n");
            }

            if (data.context().decisions() != null && !data.context().decisions().isEmpty()) {
                sb.append("## Key Architectural Decisions\n");
                for (String decision : data.context().decisions()) {
                    sb.append("- ").append(decision).append("\n");
                }
                sb.append("\n");
            }

            if (data.context().rules() != null && !data.context().rules().isEmpty()) {
                sb.append("## Development Rules\n");
                for (String rule : data.context().rules()) {
                    sb.append("- ").append(rule).append("\n");
                }
                sb.append("\n");
            }

            if (data.context().openQuestions() != null && !data.context().openQuestions().isEmpty()) {
                sb.append("## Open Questions\n");
                for (String question : data.context().openQuestions()) {
                    sb.append("- ").append(question).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }
}
