package com.llmhandoff.project;

import com.llmhandoff.error.ProjectNotFoundException;
import com.llmhandoff.user.User;
import com.llmhandoff.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Project createProject(CreateProjectRequest request, UUID userId) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        Project project = new Project(request.name(), request.description(), user);
        return projectRepository.save(project);
    }

    public Project createProject(CreateProjectRequest request) {
        return createProject(request, null);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsForUser(UUID userId) {
        if (userId == null) {
            return projectRepository.findAll();
        }
        return projectRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Project getProjectByIdAndUser(UUID id, UUID userId) {
        if (userId == null) {
            return getProjectById(id);
        }
        return projectRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + id));
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + id));
    }
}
