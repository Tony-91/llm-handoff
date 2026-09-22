package com.llmhandoff.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project createProject(CreateProjectRequest request) {
        Project project = new Project(request.name(), request.description());
        return projectRepository.save(project);
    }
}