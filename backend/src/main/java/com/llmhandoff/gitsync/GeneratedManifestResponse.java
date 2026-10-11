package com.llmhandoff.gitsync;

import com.llmhandoff.manifest.ManifestData;
import java.util.List;
import java.util.Map;

public record GeneratedManifestResponse(
        String rawJson,
        ManifestData manifestData,
        List<String> detectedFiles,
        Map<String, String> summaryHighlights
) {
}
