package com.llmhandoff.gitsync;

import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private final RestClient restClient;

    public GitHubClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "LLM-Handoff-App")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .build();
    }

    public GitHubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Normalizes repo identifier (supports full URLs like https://github.com/owner/repo or owner/repo).
     */
    public String normalizeRepoIdentifier(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Repository identifier cannot be blank");
        }
        String cleaned = input.trim();
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        if (cleaned.contains("github.com/")) {
            cleaned = cleaned.substring(cleaned.indexOf("github.com/") + 11);
        }
        cleaned = cleaned.replaceAll("^/+", "").replaceAll("/+$", "");
        String[] parts = cleaned.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid GitHub repository format. Expected 'owner/repo'");
        }
        return parts[0] + "/" + parts[1];
    }

    /**
     * Fetches repository file tree (paths) from GitHub REST API recursively.
     */
    public java.util.List<String> fetchFileTree(String repoIdentifier, String branch) {
        String normalizedRepo = normalizeRepoIdentifier(repoIdentifier);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
        String uri = "/repos/" + normalizedRepo + "/git/trees/" + targetBranch + "?recursive=1";

        log.info("Fetching repository file tree from GitHub API: {}", uri);

        try {
            Map<?, ?> responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        log.warn("GitHub tree API returned status: {}", response.getStatusCode());
                    })
                    .body(Map.class);

            if (responseBody != null && responseBody.get("tree") instanceof java.util.List<?> treeList) {
                java.util.List<String> paths = new java.util.ArrayList<>();
                for (Object item : treeList) {
                    if (item instanceof Map<?, ?> map && map.get("path") instanceof String path) {
                        paths.add(path);
                    }
                }
                return paths;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch tree from GitHub for {}@{}: {}", normalizedRepo, targetBranch, e.getMessage());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Fetches file content if it exists, returning Optional.empty() if missing or error.
     */
    public java.util.Optional<String> fetchFileContent(String repoIdentifier, String branch, String filePath) {
        String normalizedRepo = normalizeRepoIdentifier(repoIdentifier);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
        String uri = "/repos/" + normalizedRepo + "/contents/" + filePath + "?ref=" + targetBranch;

        log.info("Fetching file content from GitHub API: {}", uri);

        try {
            Map<?, ?> responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        // ignore status code to return empty
                    })
                    .body(Map.class);

            if (responseBody == null || !responseBody.containsKey("content")) {
                return java.util.Optional.empty();
            }

            String rawBase64 = (String) responseBody.get("content");
            if (rawBase64 == null) {
                return java.util.Optional.empty();
            }
            String sanitizedBase64 = rawBase64.replaceAll("\\s", "");
            byte[] decodedBytes = Base64.getDecoder().decode(sanitizedBase64);
            return java.util.Optional.of(new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not fetch file '{}' from {}@{}: {}", filePath, normalizedRepo, targetBranch, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Fetches .llmhandoff.json content from GitHub REST API.
     */
    public String fetchManifestContent(String repoIdentifier, String branch) {
        String normalizedRepo = normalizeRepoIdentifier(repoIdentifier);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";
        String uri = "/repos/" + normalizedRepo + "/contents/.llmhandoff.json?ref=" + targetBranch;

        log.info("Fetching .llmhandoff.json from GitHub API: {}", uri);

        Map<?, ?> responseBody = restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        throw new GitSyncException("'.llmhandoff.json' not found in repository " + normalizedRepo + " on branch '" + targetBranch + "'");
                    }
                    throw new GitSyncException("GitHub API client error (" + response.getStatusCode().value() + "): " + response.getStatusText());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new GitSyncException("GitHub API server error: " + response.getStatusText());
                })
                .body(Map.class);

        if (responseBody == null || !responseBody.containsKey("content")) {
            throw new GitSyncException("GitHub response did not contain file content");
        }

        String rawBase64 = (String) responseBody.get("content");
        String sanitizedBase64 = rawBase64.replaceAll("\\s", "");
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(sanitizedBase64);
            return new String(decodedBytes);
        } catch (IllegalArgumentException e) {
            throw new GitSyncException("Failed to decode Base64 content from GitHub: " + e.getMessage());
        }
    }
}
