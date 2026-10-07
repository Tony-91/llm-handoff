package com.llmhandoff.manifest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ManifestController {

    private final ManifestService manifestService;

    public ManifestController(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @PostMapping("/manifest")
    public ResponseEntity<ManifestResponse> saveManifest(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveManifestRequest request
    ) {
        ManifestResponse response = manifestService.saveManifest(projectId, request);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/manifest"))
                .body(response);
    }

    @GetMapping("/manifest")
    public ResponseEntity<ManifestResponse> getLatestManifest(@PathVariable UUID projectId) {
        ManifestResponse response = manifestService.getLatestManifest(projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/handoff-package")
    public ResponseEntity<HandoffPackageResponse> getHandoffPackage(@PathVariable UUID projectId) {
        HandoffPackageResponse response = manifestService.generateHandoffPackage(projectId);
        return ResponseEntity.ok(response);
    }
}
