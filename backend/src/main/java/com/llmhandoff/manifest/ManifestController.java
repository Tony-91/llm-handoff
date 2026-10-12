package com.llmhandoff.manifest;

import com.llmhandoff.auth.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @Valid @RequestBody SaveManifestRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getId() : null;
        ManifestResponse response = manifestService.saveManifest(projectId, request, userId);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/manifest"))
                .body(response);
    }

    @GetMapping("/manifest")
    public ResponseEntity<ManifestResponse> getLatestManifest(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getId() : null;
        ManifestResponse response = manifestService.getLatestManifest(projectId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/handoff-package")
    public ResponseEntity<HandoffPackageResponse> getHandoffPackage(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal != null ? userPrincipal.getId() : null;
        HandoffPackageResponse response = manifestService.generateHandoffPackage(projectId, userId);
        return ResponseEntity.ok(response);
    }
}
