package com.llmhandoff.project;

import com.llmhandoff.error.ProjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    // Dummy repo instance, with no real db connection
    private ProjectRepository projectRepository;

    @Mock
    private com.llmhandoff.user.UserRepository userRepository;

    @InjectMocks
    // Instantiates service, automaically injects mock repo into it
    private ProjectService projectService;

    private UUID projectId;
    private Project project;

    @BeforeEach
    // Initializes test fixtures before each test method runs.
    void setUp() {
        projectId = UUID.randomUUID();
        project = new Project("Test Project", "Test Description");
    }

//    @Test

    @Test
    void createProject_savesAndReturnsProject() {
        CreateProjectRequest request = new CreateProjectRequest("Test Project", "Test Description");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project saved = projectService.createProject(request);

        assertNotNull(saved);
        assertEquals("Test Project", saved.getName());
        assertEquals("Test Description", saved.getDescription());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void getAllProjects_returnsProjectList() {
        Project p1 = new Project("Project 1", "Desc 1");
        Project p2 = new Project("Project 2", "Desc 2");
        when(projectRepository.findAll()).thenReturn(List.of(p1, p2));

        List<Project> result = projectService.getAllProjects();

        assertEquals(2, result.size());
        assertEquals("Project 1", result.get(0).getName());
        assertEquals("Project 2", result.get(1).getName());
        verify(projectRepository).findAll();
    }

    @Test
    void getProjectById_whenProjectExists_returnsProject() {
        // Arrange
        // instead of reaching into live db, we intercept the call and return Optional.of(project)
        // Define how dependencies respond
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Act
        // Invoke actual business method to test
        Project result = projectService.getProjectById(projectId);

        // Assert & verify
        assertNotNull(result);
        assertEquals("Test Project", result.getName());
        assertEquals("Test Description", result.getDescription());
        verify(projectRepository).findById(projectId);
    }

    @Test
    void getProjectById_whenProjectDoesNotExist_throwsProjectNotFoundException() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.getProjectById(projectId));
        verify(projectRepository).findById(projectId);
    }
}
