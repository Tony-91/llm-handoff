import React, { useState, useEffect } from 'react';
import './App.css';

const API_BASE = 'http://localhost:8080/api';

function App() {
  const [projects, setProjects] = useState([]);
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Expandable cards state: map of projectId -> { expanded: boolean, manifest: object, rawInput: string, isJsonValid: boolean, loadingManifest: boolean, copySuccess: boolean, errorMessage: string }
  const [projectState, setProjectState] = useState({});

  useEffect(() => {
    fetchProjects(true);
  }, []);

  const fetchProjects = async (silent = false) => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE}/projects`);
      if (!response.ok) throw new Error('Failed to fetch projects');
      const data = await response.json();
      setProjects(data);
      setError(null);

      // Check active manifest status for each project
      data.forEach((p) => {
        fetchProjectManifest(p.id, false);
      });
    } catch (err) {
      if (!silent) {
        setError(err.message);
      } else {
        console.warn('Initial project fetch failed:', err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCreateProject = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE}/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
      });
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to create project');
      }
      await fetchProjects();
      setFormData({ name: '', description: '' });
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const toggleExpandProject = async (projectId) => {
    const current = projectState[projectId] || {};
    const willExpand = !current.expanded;

    setProjectState((prev) => ({
      ...prev,
      [projectId]: {
        ...prev[projectId],
        expanded: willExpand
      }
    }));

    if (willExpand && !current.manifest) {
      await fetchProjectManifest(projectId);
    }
  };

  const fetchProjectManifest = async (projectId, setSpinner = true) => {
    if (setSpinner) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: { ...prev[projectId], loadingManifest: true, errorMessage: null }
      }));
    }

    try {
      const response = await fetch(`${API_BASE}/projects/${projectId}/manifest`);
      if (response.ok) {
        const data = await response.json();
        setProjectState((prev) => ({
          ...prev,
          [projectId]: {
            ...prev[projectId],
            manifest: data,
            rawInput: prev[projectId]?.rawInput || data.rawContent || JSON.stringify(data.parsedData, null, 2),
            isJsonValid: true,
            loadingManifest: false
          }
        }));
      } else if (response.status === 404) {
        // No manifest uploaded yet
        setProjectState((prev) => ({
          ...prev,
          [projectId]: {
            ...prev[projectId],
            manifest: null,
            rawInput: prev[projectId]?.rawInput || '',
            isJsonValid: prev[projectId]?.isJsonValid || false,
            loadingManifest: false
          }
        }));
      } else {
        throw new Error('Failed to fetch manifest');
      }
    } catch (err) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          loadingManifest: false,
          errorMessage: setSpinner ? err.message : null
        }
      }));
    }
  };

  const validateJson = (text) => {
    if (!text || text.trim() === '') return false;
    try {
      const parsed = JSON.parse(text);
      return typeof parsed === 'object' && parsed !== null;
    } catch {
      return false;
    }
  };

  const handleRawJsonChange = (projectId, value) => {
    const isValid = validateJson(value);
    setProjectState((prev) => ({
      ...prev,
      [projectId]: {
        ...prev[projectId],
        rawInput: value,
        isJsonValid: isValid
      }
    }));
  };

  const handleFileUpload = (projectId, event) => {
    const file = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target.result;
      const isValid = validateJson(content);
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          rawInput: content,
          isJsonValid: isValid
        }
      }));
    };
    reader.readAsText(file);
  };

  const handleSaveManifest = async (projectId) => {
    const state = projectState[projectId];
    if (!state || !state.isJsonValid || !state.rawInput) return;

    setProjectState((prev) => ({
      ...prev,
      [projectId]: { ...prev[projectId], saving: true, errorMessage: null }
    }));

    try {
      const response = await fetch(`${API_BASE}/projects/${projectId}/manifest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: state.rawInput })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to save manifest');
      }

      const savedData = await response.json();
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          manifest: savedData,
          hasDraft: false,
          saving: false
        }
      }));
    } catch (err) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          saving: false,
          errorMessage: err.message
        }
      }));
    }
  };

  const handleUpdateGitConfig = async (projectId) => {
    const state = projectState[projectId] || {};
    const repoIdentifier = state.repoIdentifierInput ?? '';
    const defaultBranch = state.branchInput ?? 'main';

    setProjectState((prev) => ({
      ...prev,
      [projectId]: { ...prev[projectId], savingGit: true, errorMessage: null }
    }));

    try {
      const response = await fetch(`${API_BASE}/projects/${projectId}/git-config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repoIdentifier: repoIdentifier.trim(),
          defaultBranch: defaultBranch.trim()
        })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to update Git configuration');
      }

      const updatedProject = await response.json();
      setProjects((prev) => prev.map((p) => (p.id === projectId ? updatedProject : p)));

      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          savingGit: false,
          gitConfigSuccess: true
        }
      }));

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], gitConfigSuccess: false }
        }));
      }, 3000);
    } catch (err) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          savingGit: false,
          errorMessage: err.message
        }
      }));
    }
  };

  const handleSyncGit = async (projectId) => {
    setProjectState((prev) => ({
      ...prev,
      [projectId]: { ...prev[projectId], syncingGit: true, errorMessage: null }
    }));

    try {
      const response = await fetch(`${API_BASE}/projects/${projectId}/sync-git`, {
        method: 'POST'
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to sync manifest from GitHub');
      }

      const savedManifest = await response.json();
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          manifest: savedManifest,
          rawInput: savedManifest.rawContent || JSON.stringify(savedManifest.parsedData, null, 2),
          isJsonValid: true,
          hasDraft: false,
          syncingGit: false,
          gitSyncSuccess: true
        }
      }));

      // Refresh project list to update git_sync_status and timestamps
      fetchProjects(true);

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], gitSyncSuccess: false }
        }));
      }, 3000);
    } catch (err) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          syncingGit: false,
          errorMessage: err.message
        }
      }));
      fetchProjects(true);
    }
  };

  const handleGenerateManifest = async (projectId) => {
    setProjectState((prev) => ({
      ...prev,
      [projectId]: { ...prev[projectId], generatingManifest: true, errorMessage: null }
    }));

    try {
      const current = projectState[projectId] || {};
      const project = projects.find((p) => p.id === projectId);
      const repoIdentifier = current.repoIdentifierInput !== undefined ? current.repoIdentifierInput : (project?.repoIdentifier || '');
      const defaultBranch = current.branchInput !== undefined ? current.branchInput : (project?.defaultBranch || 'main');

      const response = await fetch(`${API_BASE}/projects/${projectId}/generate-manifest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repoIdentifier: repoIdentifier.trim(),
          defaultBranch: defaultBranch.trim()
        })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to auto-generate manifest from repository');
      }

      const generated = await response.json();
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          rawInput: generated.rawJson,
          isJsonValid: true,
          generatingManifest: false,
          hasDraft: true,
          generateSuccess: true,
          generatedHighlights: generated.summaryHighlights,
          detectedFiles: generated.detectedFiles
        }
      }));

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], generateSuccess: false }
        }));
      }, 4000);
    } catch (err) {
      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          generatingManifest: false,
          errorMessage: err.message
        }
      }));
    }
  };

  const handleCopyHandoffPackage = async (projectId) => {
    try {
      const response = await fetch(`${API_BASE}/projects/${projectId}/handoff-package`);
      if (!response.ok) throw new Error('Failed to generate handoff package');
      const data = await response.json();

      await navigator.clipboard.writeText(data.markdownPrompt);

      setProjectState((prev) => ({
        ...prev,
        [projectId]: { ...prev[projectId], copySuccess: true }
      }));

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], copySuccess: false }
        }));
      }, 3000);
    } catch (err) {
      alert(`Could not copy handoff package: ${err.message}`);
    }
  };

  return (
    <div className="app">
      <div className="container">
        <header className="header">
          <h1>LLM Handoff</h1>
          <p className="subtitle">Deterministic Context & LLM Handoff Packages from <code>.llmhandoff.json</code></p>
        </header>

        {error && <div className="error">{error}</div>}

        {/* Project Creation */}
        <div className="form-section">
          <h2>Create New Project</h2>
          <form onSubmit={handleCreateProject}>
            <input
              name="name"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="Project Name (e.g. Acme Web Service)"
              required
            />
            <textarea
              name="description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="Project Description..."
              rows="2"
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Creating...' : 'Create Project'}
            </button>
          </form>
        </div>

        {/* Expandable Project List */}
        <div className="projects-section">
          <h2>Projects ({projects.length})</h2>
          {loading && projects.length === 0 ? (
            <p className="empty-text">Loading projects...</p>
          ) : projects.length === 0 ? (
            <p className="empty-text">No projects yet. Create one above to get started.</p>
          ) : (
            <div className="projects-list">
              {projects.map((project) => {
                const state = projectState[project.id] || {};
                const isExpanded = state.expanded;
                const manifest = state.manifest;
                const parsed = manifest?.parsedData;

                return (
                  <div key={project.id} className={`project-card ${isExpanded ? 'expanded' : ''}`}>
                    <div className="project-card-header" onClick={() => toggleExpandProject(project.id)}>
                      <div className="project-title-area">
                        <h3>{project.name}</h3>
                        <p>{project.description || 'No description provided'}</p>
                      </div>
                      <div className="project-header-actions">
                        {project.repoIdentifier && (
                          <span className={`status-pill ${project.gitSyncStatus === 'SYNCED' ? 'status-git-synced' : project.gitSyncStatus === 'FAILED' ? 'status-git-failed' : 'status-git-active'}`}>
                            {project.gitSyncStatus === 'SYNCED' ? 'Git Synced' : project.gitSyncStatus === 'FAILED' ? 'Git Sync Error' : 'Git Active'}
                          </span>
                        )}
                        <span className={`status-pill ${manifest ? 'status-active' : 'status-empty'}`}>
                          {manifest ? 'Manifest Active' : 'No Manifest'}
                        </span>
                        <button type="button" className="toggle-btn">
                          {isExpanded ? 'Collapse' : 'Open Dashboard'}
                        </button>
                      </div>
                    </div>

                    {/* Expandable Content Drawer */}
                    {isExpanded && (
                      <div className="project-card-body">
                        {state.errorMessage && (
                          <div className="card-error">{state.errorMessage}</div>
                        )}

                        {/* Top Action Bar */}
                        <div className="dashboard-action-bar">
                          <button
                            type="button"
                            className={`copy-handoff-btn ${state.copySuccess ? 'copy-success' : ''}`}
                            onClick={() => handleCopyHandoffPackage(project.id)}
                            disabled={!manifest}
                          >
                            {state.copySuccess ? 'Copied to Clipboard!' : 'Copy Handoff Package'}
                          </button>

                          {(project.repoIdentifier || state.repoIdentifierInput) && (
                            <button
                              type="button"
                              className={`generate-manifest-btn ${state.hasDraft ? 'btn-draft-generated' : ''}`}
                              onClick={() => handleGenerateManifest(project.id)}
                              disabled={state.generatingManifest || state.hasDraft}
                            >
                              {state.generatingManifest ? 'Inspecting Repo...' : state.hasDraft ? 'Draft Generated' : 'Auto-Generate Manifest from Repo'}
                            </button>
                          )}

                          {project.repoIdentifier && (
                            <button
                              type="button"
                              className="sync-git-btn"
                              onClick={() => handleSyncGit(project.id)}
                              disabled={state.syncingGit}
                            >
                              {state.syncingGit ? 'Syncing...' : state.gitSyncSuccess ? 'Synced from Git!' : 'Sync from GitHub'}
                            </button>
                          )}
                        </div>

                        {/* GitHub Repo Integration Section */}
                        <div className="git-config-section">
                          <h4>GitHub Repository Sync</h4>
                          <div className="git-config-grid">
                            <div className="git-input-group">
                              <label>Repository (owner/repo or GitHub URL)</label>
                              <input
                                type="text"
                                placeholder="e.g. facebook/react or https://github.com/owner/repo"
                                value={state.repoIdentifierInput !== undefined ? state.repoIdentifierInput : (project.repoIdentifier || '')}
                                onChange={(e) => setProjectState((prev) => ({
                                  ...prev,
                                  [project.id]: {
                                    ...prev[project.id],
                                    repoIdentifierInput: e.target.value
                                  }
                                }))}
                              />
                            </div>
                            <div className="git-input-group small">
                              <label>Branch</label>
                              <input
                                type="text"
                                placeholder="main"
                                value={state.branchInput !== undefined ? state.branchInput : (project.defaultBranch || 'main')}
                                onChange={(e) => setProjectState((prev) => ({
                                  ...prev,
                                  [project.id]: {
                                    ...prev[project.id],
                                    branchInput: e.target.value
                                  }
                                }))}
                              />
                            </div>
                            <div className="git-action-group">
                              <button
                                type="button"
                                className="save-git-btn"
                                onClick={() => handleUpdateGitConfig(project.id)}
                                disabled={state.savingGit}
                              >
                                {state.savingGit ? 'Saving...' : state.gitConfigSuccess ? 'Saved' : 'Save Git Config'}
                              </button>
                            </div>
                          </div>
                          {project.repoIdentifier && (
                            <div className="git-status-meta">
                              <span>Status: <strong>{project.gitSyncStatus === 'SYNCED' ? 'Synced' : project.gitSyncStatus === 'FAILED' ? 'Error' : 'Active'}</strong></span>
                              {project.lastSyncedAt && (
                                <span> • Last synced: {new Date(project.lastSyncedAt).toLocaleString()}</span>
                              )}
                              {project.gitSyncError && (
                                <p className="git-error-text">Sync error: {project.gitSyncError}</p>
                              )}
                            </div>
                          )}
                        </div>

                        {/* Structured Dashboard Cards */}
                        {state.loadingManifest ? (
                          <p className="empty-text">Loading manifest details...</p>
                        ) : parsed ? (
                          <div className="dashboard-grid">
                            {/* Tech Stack Card */}
                            {parsed.environment && (
                              <div className="dashboard-card">
                                <h4>Tech Stack & Environment</h4>
                                <div className="badge-group">
                                  {parsed.environment.language && <span className="chip chip-blue">Lang: {parsed.environment.language}</span>}
                                  {parsed.environment.framework && <span className="chip chip-purple">Framework: {parsed.environment.framework}</span>}
                                  {parsed.environment.database && <span className="chip chip-green">DB: {parsed.environment.database}</span>}
                                  {parsed.environment.buildTool && <span className="chip chip-orange">Build: {parsed.environment.buildTool}</span>}
                                  {parsed.environment.runtime && <span className="chip chip-teal">Runtime: {parsed.environment.runtime}</span>}
                                </div>
                              </div>
                            )}

                            {/* Commands Card */}
                            {parsed.commands && Object.keys(parsed.commands).length > 0 && (
                              <div className="dashboard-card">
                                <h4>Commands</h4>
                                <div className="commands-list">
                                  {Object.entries(parsed.commands).map(([cmdKey, cmdVal]) => (
                                    <div key={cmdKey} className="command-row">
                                      <span className="cmd-name">{cmdKey}:</span>
                                      <code>{cmdVal}</code>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {/* Architecture & Decisions */}
                            {parsed.context && (
                              <div className="dashboard-card full-width">
                                <h4>Context & Architectural Rules</h4>
                                {parsed.context.architecture && (
                                  <p className="architecture-text"><strong>Architecture:</strong> {parsed.context.architecture}</p>
                                )}

                                {parsed.context.decisions && parsed.context.decisions.length > 0 && (
                                  <div className="checklist-block">
                                    <strong>Decisions:</strong>
                                    <ul>
                                      {parsed.context.decisions.map((dec, i) => (
                                        <li key={i}>{dec}</li>
                                      ))}
                                    </ul>
                                  </div>
                                )}

                                {parsed.context.rules && parsed.context.rules.length > 0 && (
                                  <div className="checklist-block">
                                    <strong>Development Rules:</strong>
                                    <ul>
                                      {parsed.context.rules.map((rule, i) => (
                                        <li key={i}>{rule}</li>
                                      ))}
                                    </ul>
                                  </div>
                                )}

                                {parsed.context.openQuestions && parsed.context.openQuestions.length > 0 && (
                                  <div className="checklist-block">
                                    <strong>Open Questions:</strong>
                                    <ul>
                                      {parsed.context.openQuestions.map((q, i) => (
                                        <li key={i}>{q}</li>
                                      ))}
                                    </ul>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        ) : null}

                        {/* JSON Ingestion & Editor Section */}
                        <div className="manifest-uploader-section">
                          <h4>Upload or Edit <code>.llmhandoff.json</code></h4>

                          {/* Option B: Field Previews / Pill Highlights */}
                          {state.generatedHighlights && (
                            <div className="preview-highlights-card">
                              <div className="preview-highlights-header">
                                <h5>Auto-Detected Configuration Fields</h5>
                                <span className="preview-pill-badge">Draft Candidate</span>
                              </div>
                              <div className="preview-pill-group">
                                {state.generatedHighlights.Language && (
                                  <div className="highlight-pill pill-blue">
                                    <span className="pill-title">Language</span>
                                    <span className="pill-val">{state.generatedHighlights.Language}</span>
                                  </div>
                                )}
                                {state.generatedHighlights.Framework && (
                                  <div className="highlight-pill pill-purple">
                                    <span className="pill-title">Framework</span>
                                    <span className="pill-val">{state.generatedHighlights.Framework}</span>
                                  </div>
                                )}
                                {state.generatedHighlights.Database && (
                                  <div className="highlight-pill pill-green">
                                    <span className="pill-title">Database</span>
                                    <span className="pill-val">{state.generatedHighlights.Database}</span>
                                  </div>
                                )}
                                {state.generatedHighlights['Build Tool'] && (
                                  <div className="highlight-pill pill-orange">
                                    <span className="pill-title">Build Tool</span>
                                    <span className="pill-val">{state.generatedHighlights['Build Tool']}</span>
                                  </div>
                                )}
                                {state.generatedHighlights['Detected Files'] && (
                                  <div className="highlight-pill pill-gray">
                                    <span className="pill-title">Detected Files</span>
                                    <span className="pill-val">{state.generatedHighlights['Detected Files']}</span>
                                  </div>
                                )}
                              </div>
                              <p className="preview-note">Review or modify the candidate JSON below, then click "Save & Activate Manifest" to apply.</p>
                            </div>
                          )}

                          <div className="file-input-wrapper">
                            <label className="file-label">
                              Choose <code>.llmhandoff.json</code> File
                              <input
                                type="file"
                                accept=".json,application/json"
                                onChange={(e) => handleFileUpload(project.id, e)}
                              />
                            </label>
                          </div>

                          <div className="json-editor-container">
                            <div className="validation-bar">
                              <span className={`validation-badge ${state.isJsonValid ? 'valid' : 'invalid'}`}>
                                {state.isJsonValid ? 'Valid JSON' : 'Invalid / Empty JSON'}
                              </span>
                            </div>
                            <textarea
                              className={`json-textarea ${state.isJsonValid ? 'json-valid' : state.rawInput ? 'json-invalid' : ''}`}
                              rows="10"
                              placeholder={`{\n  "schemaVersion": "1.0",\n  "environment": {\n    "language": "Java 21",\n    "framework": "Spring Boot 3.5.0"\n  },\n  "commands": {\n    "build": "mvn compile",\n    "test": "mvn test"\n  }\n}`}
                              value={state.rawInput || ''}
                              onChange={(e) => handleRawJsonChange(project.id, e.target.value)}
                            />
                          </div>

                          <button
                            type="button"
                            className="save-manifest-btn"
                            onClick={() => handleSaveManifest(project.id)}
                            disabled={!state.isJsonValid || state.saving}
                          >
                            {state.saving ? 'Saving...' : 'Save & Activate Manifest'}
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;
