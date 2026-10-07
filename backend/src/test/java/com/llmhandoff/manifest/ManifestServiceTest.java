package com.llmhandoff.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.error.ManifestNotFoundException;
import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.project.Project;
import com.llmhandoff.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManifestServiceTest {

    @Mock
    private ManifestRepository manifestRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ManifestService manifestService;

    private UUID projectId;
    private Project project;
    private String validManifestJson;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test Project", "Test Description");

        validManifestJson = """
                {
                  "schemaVersion": "1.0",
                  "project": {
                    "name": "Test Project",
                    "description": "Test Description"
                  },
                  "environment": {
                    "language": "Java 21",
                    "framework": "Spring Boot 3.5.0",
                    "database": "MySQL 8.0",
                    "buildTool": "Maven"
                  },
                  "commands": {
                    "build": "mvn clean compile",
                    "test": "mvn test"
                  },
                  "context": {
                    "architecture": "Layered MVC",
                    "decisions": ["Use UUIDs"],
                    "rules": ["Write unit tests"],
                    "openQuestions": ["None"]
                  }
                }
                """;
    }

    @Test
    void saveManifest_success() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(manifestRepository.save(any(Manifest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaveManifestRequest request = new SaveManifestRequest(validManifestJson);
        ManifestResponse response = manifestService.saveManifest(projectId, request);

        assertNotNull(response);
        assertEquals("1.0", response.schemaVersion());
        assertNotNull(response.parsedData());
        assertEquals("Java 21", response.parsedData().environment().language());
        assertEquals("mvn test", response.parsedData().commands().get("test"));
        verify(manifestRepository).save(any(Manifest.class));
    }

    @Test
    void saveManifest_whenInvalidJson_throwsIllegalArgumentException() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        SaveManifestRequest request = new SaveManifestRequest("invalid json {");

        assertThrows(IllegalArgumentException.class, () -> manifestService.saveManifest(projectId, request));
        verify(manifestRepository, never()).save(any(Manifest.class));
    }

    @Test
    void saveManifest_whenProjectNotFound_throwsProjectNotFoundException() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        SaveManifestRequest request = new SaveManifestRequest(validManifestJson);

        assertThrows(ProjectNotFoundException.class, () -> manifestService.saveManifest(projectId, request));
        verify(manifestRepository, never()).save(any(Manifest.class));
    }

    @Test
    void getLatestManifest_success() {
        Manifest manifest = new Manifest(project, "1.0", validManifestJson);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(manifestRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(Optional.of(manifest));

        ManifestResponse response = manifestService.getLatestManifest(projectId);

        assertNotNull(response);
        assertEquals("1.0", response.schemaVersion());
        assertEquals("Test Project", response.parsedData().project().name());
    }

    @Test
    void getLatestManifest_whenProjectNotFound_throwsProjectNotFoundException() {
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(ProjectNotFoundException.class, () -> manifestService.getLatestManifest(projectId));
    }

    @Test
    void getLatestManifest_whenManifestNotFound_throwsManifestNotFoundException() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(manifestRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(Optional.empty());

        assertThrows(ManifestNotFoundException.class, () -> manifestService.getLatestManifest(projectId));
    }

    @Test
    void generateHandoffPackage_success() {
        Manifest manifest = new Manifest(project, "1.0", validManifestJson);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(manifestRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(Optional.of(manifest));

        HandoffPackageResponse response = manifestService.generateHandoffPackage(projectId);

        assertNotNull(response);
        assertEquals(project.getName(), response.projectName());
        assertTrue(response.markdownPrompt().contains("# Project Context: Test Project"));
        assertTrue(response.markdownPrompt().contains("Java 21"));
        assertTrue(response.markdownPrompt().contains("`mvn test`"));
        assertTrue(response.markdownPrompt().contains("Use UUIDs"));
        assertTrue(response.markdownPrompt().contains("Write unit tests"));
    }
}
