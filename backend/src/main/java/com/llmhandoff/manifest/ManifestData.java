package com.llmhandoff.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ManifestData(
        String schemaVersion,
        ProjectInfo project,
        EnvironmentInfo environment,
        Map<String, String> commands,
        ProjectContext context
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectInfo(
            String name,
            String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvironmentInfo(
            String language,
            String framework,
            String database,
            String buildTool,
            String runtime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectContext(
            String architecture,
            List<String> decisions,
            List<String> rules,
            List<String> openQuestions
    ) {}
}
