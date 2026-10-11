package com.llmhandoff.gitsync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.manifest.ManifestData;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepoInspectionService {

    private static final Logger log = LoggerFactory.getLogger(RepoInspectionService.class);

    private final ProjectRepository projectRepository;
    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;

    public RepoInspectionService(
            ProjectRepository projectRepository,
            GitHubClient gitHubClient,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GeneratedManifestResponse generateManifestFromRepo(UUID projectId, GenerateManifestRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        String repoIdentifier = (request != null && request.repoIdentifier() != null && !request.repoIdentifier().isBlank())
                ? request.repoIdentifier()
                : project.getRepoIdentifier();

        String branch = (request != null && request.defaultBranch() != null && !request.defaultBranch().isBlank())
                ? request.defaultBranch()
                : (project.getDefaultBranch() != null ? project.getDefaultBranch() : "main");

        if (repoIdentifier == null || repoIdentifier.isBlank()) {
            throw new GitSyncException("No GitHub repository configured for this project. Please provide a repository identifier (owner/repo).");
        }

        String normalizedRepo = gitHubClient.normalizeRepoIdentifier(repoIdentifier);
        log.info("Inspecting repository {} on branch {} for project {}", normalizedRepo, branch, projectId);

        List<String> treePaths = gitHubClient.fetchFileTree(normalizedRepo, branch);
        Set<String> allPaths = Set.copyOf(treePaths);

        List<String> detectedFiles = new ArrayList<>();
        Map<String, String> fileContents = new LinkedHashMap<>();

        List<String> candidatePaths = List.of(
                "pom.xml", "backend/pom.xml", "server/pom.xml", "api/pom.xml",
                "build.gradle", "backend/build.gradle", "build.gradle.kts",
                "package.json", "frontend/package.json", "client/package.json", "web/package.json", "app/package.json",
                "docker-compose.yml", "docker-compose.yaml", "compose.yaml",
                "Dockerfile", "backend/Dockerfile", "frontend/Dockerfile",
                "requirements.txt", "pyproject.toml", "Pipfile",
                "Cargo.toml", "go.mod",
                "README.md"
        );

        for (String candidate : candidatePaths) {
            boolean shouldFetch = allPaths.isEmpty() || allPaths.contains(candidate);
            if (shouldFetch) {
                Optional<String> content = gitHubClient.fetchFileContent(normalizedRepo, branch, candidate);
                if (content.isPresent() && !content.get().isBlank()) {
                    detectedFiles.add(candidate);
                    fileContents.put(candidate, content.get());
                }
            }
        }

        if (detectedFiles.isEmpty()) {
            log.warn("No standard descriptor files detected in repository {} on branch {}", normalizedRepo, branch);
        }

        ManifestData manifestData = buildManifestFromInspection(project, fileContents);

        String rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifestData);
        } catch (Exception e) {
            throw new GitSyncException("Failed to format generated manifest: " + e.getMessage(), e);
        }

        Map<String, String> highlights = new LinkedHashMap<>();
        if (manifestData.environment() != null) {
            if (manifestData.environment().language() != null) highlights.put("Language", manifestData.environment().language());
            if (manifestData.environment().framework() != null) highlights.put("Framework", manifestData.environment().framework());
            if (manifestData.environment().database() != null) highlights.put("Database", manifestData.environment().database());
            if (manifestData.environment().buildTool() != null) highlights.put("Build Tool", manifestData.environment().buildTool());
        }
        highlights.put("Detected Files", detectedFiles.isEmpty() ? "None (Fallback defaults used)" : String.join(", ", detectedFiles));

        return new GeneratedManifestResponse(rawJson, manifestData, detectedFiles, highlights);
    }

    private ManifestData buildManifestFromInspection(Project project, Map<String, String> files) {
        List<String> languages = new ArrayList<>();
        List<String> frameworks = new ArrayList<>();
        List<String> databases = new ArrayList<>();
        List<String> buildTools = new ArrayList<>();
        List<String> runtimes = new ArrayList<>();
        Map<String, String> commands = new LinkedHashMap<>();
        List<String> decisions = new ArrayList<>();
        List<String> rules = new ArrayList<>();

        String projectName = project.getName() != null ? project.getName() : "My Project";
        String projectDescription = project.getDescription();

        // 1. Inspect README.md for project info
        if (files.containsKey("README.md")) {
            String readme = files.get("README.md");
            Matcher headingMatcher = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE).matcher(readme);
            if (headingMatcher.find() && (projectName.equals("My Project") || projectName.isBlank())) {
                projectName = headingMatcher.group(1).trim();
            }
            if (projectDescription == null || projectDescription.isBlank()) {
                for (String line : readme.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && !trimmed.startsWith("!") && !trimmed.startsWith("[") && trimmed.length() > 20) {
                        projectDescription = trimmed;
                        break;
                    }
                }
            }
        }

        // 2. Inspect Java / Maven / Gradle
        String pom = files.getOrDefault("pom.xml", files.getOrDefault("backend/pom.xml", files.get("server/pom.xml")));
        if (pom != null) {
            String javaVersion = "Java 21";
            Matcher jv = Pattern.compile("<java\\.version>(.*?)</java\\.version>").matcher(pom);
            if (jv.find()) {
                javaVersion = "Java " + jv.group(1).trim();
            } else {
                Matcher ms = Pattern.compile("<maven\\.compiler\\.source>(.*?)</maven\\.compiler\\.source>").matcher(pom);
                if (ms.find()) {
                    javaVersion = "Java " + ms.group(1).trim();
                }
            }
            languages.add(javaVersion);
            buildTools.add("Maven");
            runtimes.add("JVM");

            if (pom.contains("spring-boot")) {
                frameworks.add("Spring Boot 3.5.0");
                commands.put("build", "mvn clean compile");
                commands.put("test", "mvn test");
                commands.put("run", "mvn spring-boot:run");
            } else {
                commands.put("build", "mvn compile");
                commands.put("test", "mvn test");
            }

            if (pom.contains("mysql")) databases.add("MySQL 8.0");
            if (pom.contains("postgresql")) databases.add("PostgreSQL");
            if (pom.contains("h2")) databases.add("H2 (in-memory test DB)");
            if (pom.contains("flyway")) decisions.add("Use Flyway database schema migrations for version control");
            if (pom.contains("spring-boot-starter-data-jpa")) decisions.add("Use Spring Data JPA for data persistence");
        }

        String gradle = files.getOrDefault("build.gradle", files.getOrDefault("backend/build.gradle", files.get("build.gradle.kts")));
        if (gradle != null) {
            if (!languages.contains("Java 21") && !languages.contains("Java 17")) languages.add("Java / Kotlin");
            buildTools.add("Gradle");
            if (gradle.contains("spring")) frameworks.add("Spring Boot");
            commands.put("build", "./gradlew build");
            commands.put("test", "./gradlew test");
            commands.put("run", "./gradlew bootRun");
        }

        // 3. Inspect Node / React / TypeScript (package.json)
        String pkgJsonStr = files.getOrDefault("package.json", files.getOrDefault("frontend/package.json", files.get("client/package.json")));
        if (pkgJsonStr != null) {
            try {
                JsonNode pkg = objectMapper.readTree(pkgJsonStr);
                boolean isTs = pkg.has("devDependencies") && pkg.get("devDependencies").has("typescript")
                        || pkg.has("dependencies") && pkg.get("dependencies").has("typescript");

                if (isTs) {
                    languages.add("TypeScript");
                } else if (!languages.contains("JavaScript")) {
                    languages.add("JavaScript");
                }

                buildTools.add("npm");
                runtimes.add("Node.js");

                JsonNode deps = pkg.get("dependencies");
                if (deps != null) {
                    if (deps.has("react")) frameworks.add("React");
                    if (deps.has("next")) frameworks.add("Next.js");
                    if (deps.has("vue")) frameworks.add("Vue.js");
                    if (deps.has("express")) frameworks.add("Express.js");
                    if (deps.has("tailwindcss")) decisions.add("Use Tailwind CSS for responsive styling");
                }

                if (!commands.containsKey("setup")) commands.put("setup", "npm install");
                if (!commands.containsKey("build")) commands.put("build", "npm run build");
                if (!commands.containsKey("test")) commands.put("test", "npm test");
                if (!commands.containsKey("run")) commands.put("run", "npm start");
            } catch (Exception e) {
                log.warn("Failed to parse package.json: {}", e.getMessage());
            }
        }

        // 4. Inspect Docker & Compose
        String compose = files.getOrDefault("docker-compose.yml", files.getOrDefault("docker-compose.yaml", files.get("compose.yaml")));
        if (compose != null) {
            if (compose.contains("mysql") && !databases.contains("MySQL 8.0")) databases.add("MySQL 8.0");
            if (compose.contains("postgres") && !databases.contains("PostgreSQL")) databases.add("PostgreSQL");
            if (compose.contains("redis")) databases.add("Redis");
            if (compose.contains("mongo")) databases.add("MongoDB");

            commands.put("setup", "docker-compose up -d");
            decisions.add("Use Docker Compose for local database and service orchestration");
        }

        if (files.containsKey("Dockerfile") || files.containsKey("backend/Dockerfile")) {
            decisions.add("Containerize application services with multi-stage Docker builds");
        }

        // 5. Inspect Python
        if (files.containsKey("requirements.txt") || files.containsKey("pyproject.toml")) {
            languages.add("Python 3.12");
            buildTools.add("pip");
            String reqs = files.getOrDefault("requirements.txt", files.get("pyproject.toml"));
            if (reqs.contains("fastapi")) frameworks.add("FastAPI");
            if (reqs.contains("django")) frameworks.add("Django");
            if (reqs.contains("flask")) frameworks.add("Flask");
            if (!commands.containsKey("test")) commands.put("test", "pytest");
        }

        // 6. Inspect Rust / Go
        if (files.containsKey("Cargo.toml")) {
            languages.add("Rust");
            buildTools.add("Cargo");
            commands.put("build", "cargo build");
            commands.put("test", "cargo test");
            commands.put("run", "cargo run");
        }
        if (files.containsKey("go.mod")) {
            languages.add("Go");
            commands.put("build", "go build");
            commands.put("test", "go test ./...");
            commands.put("run", "go run .");
        }

        // Default fallbacks if no descriptors found
        if (languages.isEmpty()) languages.add("Java 21");
        if (frameworks.isEmpty()) frameworks.add("Spring Boot 3.5.0");
        if (databases.isEmpty()) databases.add("MySQL 8.0");
        if (buildTools.isEmpty()) buildTools.add("Maven");
        if (commands.isEmpty()) {
            commands.put("setup", "docker-compose up -d");
            commands.put("build", "mvn clean compile");
            commands.put("test", "mvn test");
            commands.put("run", "mvn spring-boot:run");
        }

        // Add standard architectural rules
        rules.add("Always write unit tests for services and slice tests for controllers");
        rules.add("Keep API responses structured and validated");
        rules.add("Never commit sensitive secrets or environment credentials into repository");

        List<String> openQuestions = List.of(
                "Review and refine auto-detected commands and rules before exporting handoff packages"
        );

        String architecture = "Full-stack application architecture combining " + String.join(" & ", frameworks) + ".";

        ManifestData.ProjectInfo projectInfo = new ManifestData.ProjectInfo(
                projectName,
                projectDescription != null ? projectDescription : "Structured LLM handoff and context management."
        );

        ManifestData.EnvironmentInfo environmentInfo = new ManifestData.EnvironmentInfo(
                String.join(" / ", languages),
                String.join(" / ", frameworks),
                String.join(" / ", databases),
                String.join(" / ", buildTools),
                runtimes.isEmpty() ? "JVM" : String.join(" / ", runtimes)
        );

        ManifestData.ProjectContext context = new ManifestData.ProjectContext(
                architecture,
                decisions,
                rules,
                openQuestions
        );

        return new ManifestData("1.0", projectInfo, environmentInfo, commands, context);
    }
}
