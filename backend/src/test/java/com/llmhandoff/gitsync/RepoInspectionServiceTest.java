package com.llmhandoff.gitsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoInspectionServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GitHubClient gitHubClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RepoInspectionService repoInspectionService;

    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test App", "Description");
        project.setRepoIdentifier("my-org/my-app");
        project.setDefaultBranch("main");
    }

    @Test
    @DisplayName("generateManifestFromRepo inspects pom.xml and package.json to construct manifest")
    void generateManifestFromRepo_fullStackInspection() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(gitHubClient.normalizeRepoIdentifier("my-org/my-app")).thenReturn("my-org/my-app");
        when(gitHubClient.fetchFileTree("my-org/my-app", "main"))
                .thenReturn(List.of("backend/pom.xml", "frontend/package.json", "docker-compose.yml", "README.md"));

        String pomContent = """
                <project>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.mysql</groupId>
                            <artifactId>mysql-connector-j</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        String pkgJsonContent = """
                {
                    "dependencies": {
                        "react": "^18.2.0"
                    },
                    "devDependencies": {
                        "typescript": "^5.0.0"
                    }
                }
                """;

        String composeContent = """
                services:
                  mysql:
                    image: mysql:8.0
                """;

        String readmeContent = """
                # My Awesome App
                A full stack application for demonstration.
                """;

        when(gitHubClient.fetchFileContent("my-org/my-app", "main", "backend/pom.xml")).thenReturn(Optional.of(pomContent));
        when(gitHubClient.fetchFileContent("my-org/my-app", "main", "frontend/package.json")).thenReturn(Optional.of(pkgJsonContent));
        when(gitHubClient.fetchFileContent("my-org/my-app", "main", "docker-compose.yml")).thenReturn(Optional.of(composeContent));
        when(gitHubClient.fetchFileContent("my-org/my-app", "main", "README.md")).thenReturn(Optional.of(readmeContent));

        GeneratedManifestResponse response = repoInspectionService.generateManifestFromRepo(projectId, null);

        assertNotNull(response);
        assertNotNull(response.rawJson());
        assertNotNull(response.manifestData());
        assertTrue(response.detectedFiles().contains("backend/pom.xml"));
        assertTrue(response.detectedFiles().contains("frontend/package.json"));

        assertEquals("Java 21 / TypeScript", response.manifestData().environment().language());
        assertTrue(response.manifestData().environment().framework().contains("Spring Boot"));
        assertTrue(response.manifestData().environment().framework().contains("React"));
        assertEquals("MySQL 8.0", response.manifestData().environment().database());
        assertEquals("Maven / npm", response.manifestData().environment().buildTool());
        assertTrue(response.manifestData().commands().containsKey("test"));
        assertNotNull(response.summaryHighlights().get("Language"));
    }

    @Test
    @DisplayName("generateManifestFromRepo throws GitSyncException when no repo is configured")
    void generateManifestFromRepo_noRepoConfigured() {
        project.setRepoIdentifier(null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        GitSyncException ex = assertThrows(GitSyncException.class, () ->
                repoInspectionService.generateManifestFromRepo(projectId, null));

        assertTrue(ex.getMessage().contains("No GitHub repository configured"));
    }

    @Test
    @DisplayName("generateManifestFromRepo throws ProjectNotFoundException for unknown project")
    void generateManifestFromRepo_projectNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () ->
                repoInspectionService.generateManifestFromRepo(projectId, null));
    }
}
