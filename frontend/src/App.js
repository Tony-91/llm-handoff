import React, { useState, useEffect, useCallback } from 'react';
import './App.css';
import AuthModal from './components/AuthModal';

const API_BASE = 'http://localhost:8080/api';

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('auth_token') || '');
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('auth_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);

  const [projects, setProjects] = useState([]);
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Expandable cards state: map of projectId -> { ... }
  const [projectState, setProjectState] = useState({});

  // Helper authenticated fetch
  const authFetch = useCallback(
    async (url, options = {}) => {
      const headers = {
        ...(options.headers || {})
      };

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const res = await fetch(url, { ...options, headers });

      if (res.status === 401) {
        // Token expired or invalid
        handleLogout();
        setIsAuthModalOpen(true);
      }

      return res;
    },
    [token]
  );

  const fetchCurrentUser = useCallback(async (authToken) => {
    try {
      const res = await fetch(`${API_BASE}/auth/me`, {
        headers: { Authorization: `Bearer ${authToken}` }
      });
      if (res.ok) {
        const userData = await res.json();
        setUser(userData);
        localStorage.setItem('auth_user', JSON.stringify(userData));
      } else {
        handleLogout();
      }
    } catch (e) {
      console.error('Failed to fetch user profile:', e);
    }
  }, []);

  // Parse token from URL after OAuth2 redirect
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');

    if (tokenFromUrl) {
      localStorage.setItem('auth_token', tokenFromUrl);
      setToken(tokenFromUrl);
      fetchCurrentUser(tokenFromUrl);

      // Clean up token from browser URL
      const cleanUrl = window.location.pathname;
      window.history.replaceState({}, document.title, cleanUrl);
    } else if (token && !user) {
      fetchCurrentUser(token);
    }
  }, [fetchCurrentUser, token, user]);

  const handleAuthSuccess = (newToken, newUser) => {
    setToken(newToken);
    setUser(newUser);
    localStorage.setItem('auth_token', newToken);
    localStorage.setItem('auth_user', JSON.stringify(newUser));
  };

  const handleLogout = () => {
    setToken('');
    setUser(null);
    setProjects([]);
    setProjectState({});
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
  };

  const fetchProjectManifest = useCallback(
    async (projectId, setSpinner = true) => {
      if (setSpinner) {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], loadingManifest: true, errorMessage: null }
        }));
      }

      try {
        const response = await authFetch(`${API_BASE}/projects/${projectId}/manifest`);
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
          setProjectState((prev) => ({
            ...prev,
            [projectId]: {
              ...prev[projectId],
              loadingManifest: false,
              errorMessage: 'Failed to load manifest.'
            }
          }));
        }
      } catch (err) {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: {
            ...prev[projectId],
            loadingManifest: false,
            errorMessage: err.message
          }
        }));
      }
    },
    [authFetch]
  );

  const fetchProjects = useCallback(
    async (silent = false) => {
      if (!token) return;
      try {
        setLoading(true);
        const response = await authFetch(`${API_BASE}/projects`);
        if (!response.ok) throw new Error('Failed to fetch projects');
        const data = await response.json();
        setProjects(data);
        setError(null);

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
    },
    [token, authFetch, fetchProjectManifest]
  );

  useEffect(() => {
    if (token) {
      fetchProjects(true);
    }
  }, [token, fetchProjects]);

  const handleCreateProject = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      const response = await authFetch(`${API_BASE}/projects`, {
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

  const validateJson = (text) => {
    if (!text || !text.trim()) return false;
    try {
      const parsed = JSON.parse(text);
      return typeof parsed === 'object' && parsed !== null;
    } catch (e) {
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

  const handleFileUpload = (projectId, file) => {
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
      const response = await authFetch(`${API_BASE}/projects/${projectId}/manifest`, {
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
          saving: false,
          saveSuccess: true
        }
      }));

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], saveSuccess: false }
        }));
      }, 3000);
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

  const handleSaveGitConfig = async (projectId, repoIdentifier, defaultBranch) => {
    if (!repoIdentifier || !repoIdentifier.trim()) {
      alert('Please enter a repository in "owner/repo" format.');
      return;
    }

    setProjectState((prev) => ({
      ...prev,
      [projectId]: { ...prev[projectId], savingGitConfig: true, errorMessage: null }
    }));

    try {
      const response = await authFetch(`${API_BASE}/projects/${projectId}/git-config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          repoIdentifier: repoIdentifier.trim(),
          defaultBranch: (defaultBranch || 'main').trim()
        })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to update Git configuration');
      }

      const updatedProject = await response.json();
      setProjects((prev) =>
        prev.map((p) => (p.id === projectId ? { ...p, ...updatedProject } : p))
      );

      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          savingGitConfig: false,
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
          savingGitConfig: false,
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
      const response = await authFetch(`${API_BASE}/projects/${projectId}/sync-git`, {
        method: 'POST'
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to sync manifest from GitHub');
      }

      const savedManifest = await response.json();
      setProjects((prev) =>
        prev.map((p) =>
          p.id === projectId
            ? { ...p, gitSyncStatus: 'SYNCED', lastSyncedAt: new Date().toISOString() }
            : p
        )
      );

      setProjectState((prev) => ({
        ...prev,
        [projectId]: {
          ...prev[projectId],
          manifest: savedManifest,
          rawInput: savedManifest.rawContent || JSON.stringify(savedManifest.parsedData, null, 2),
          isJsonValid: true,
          syncingGit: false,
          syncSuccess: true
        }
      }));

      setTimeout(() => {
        setProjectState((prev) => ({
          ...prev,
          [projectId]: { ...prev[projectId], syncSuccess: false }
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
    }
  };

  const handleGenerateManifest = async (projectId, repoIdentifier, defaultBranch) => {
    if (!repoIdentifier || !repoIdentifier.trim()) {
      alert('Please configure a valid repository identifier first (owner/repo).');
      return;
    }

    setProjectState((prev) => ({
      ...prev,
      [projectId]: {
        ...prev[projectId],
        generatingManifest: true,
        errorMessage: null,
        generatedHighlights: null,
        detectedFiles: null
      }
    }));

    try {
      const response = await authFetch(`${API_BASE}/projects/${projectId}/generate-manifest`, {
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
      const response = await authFetch(`${API_BASE}/projects/${projectId}/handoff-package`);
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
        {/* Navigation & Auth Bar */}
        <nav className="navbar">
          <div className="nav-brand">
            <span>Project Handoff</span>
            <span className="nav-brand-badge">v1.0-MVP</span>
          </div>
          <div className="nav-auth-controls">
            {token && user ? (
              <div className="user-badge">
                <span className="user-avatar">👤</span>
                <span>{user.username || user.email}</span>
                <button type="button" className="btn-logout" onClick={handleLogout}>
                  Log Out
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="btn-sign-in"
                onClick={() => setIsAuthModalOpen(true)}
              >
                Sign In / Sign Up
              </button>
            )}
          </div>
        </nav>

        <header className="header">
          <h1>Deterministic LLM Handoffs</h1>
          <p className="subtitle">
            Extract, structure, and serialize developer context into model-agnostic packages via <code>.llmhandoff.json</code>
          </p>
        </header>

        {error && <div className="error">{error}</div>}

        {!token ? (
          /* Unauthenticated Lockout Landing Screen */
          <div className="lockout-banner">
            <h2>Sign In to Access Your Projects</h2>
            <p>
              Project Handoff securely isolates project state, architectural decisions, and LLM manifests per developer account.
            </p>
            <button
              type="button"
              className="lockout-btn"
              onClick={() => setIsAuthModalOpen(true)}
            >
              Sign In / Create Account
            </button>
          </div>
        ) : (
          /* Authenticated Workspace */
          <>
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
              <h2>My Projects ({projects.length})</h2>
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
                            {project.description && <p className="project-desc">{project.description}</p>}
                          </div>
                          <div className="project-card-meta">
                            {manifest ? (
                              <span className="badge badge-active">Manifest Active</span>
                            ) : (
                              <span className="badge badge-missing">No Manifest</span>
                            )}
                            <span className="expand-chevron">{isExpanded ? '▲ Collapse' : '▼ Expand'}</span>
                          </div>
                        </div>

                        {isExpanded && (
                          <div className="project-card-body">
                            {state.errorMessage && <div className="card-error">{state.errorMessage}</div>}

                            {/* GitHub Git Sync Configuration Section */}
                            <div className="git-sync-section">
                              <h4>GitHub Integration & Sync</h4>
                              <div className="git-config-row">
                                <div className="git-input-group">
                                  <label>GitHub Repository (owner/repo)</label>
                                  <input
                                    type="text"
                                    placeholder="e.g. facebook/react"
                                    defaultValue={project.repoIdentifier || ''}
                                    id={`repo-input-${project.id}`}
                                  />
                                </div>
                                <div className="git-input-group branch-group">
                                  <label>Branch</label>
                                  <input
                                    type="text"
                                    placeholder="main"
                                    defaultValue={project.defaultBranch || 'main'}
                                    id={`branch-input-${project.id}`}
                                  />
                                </div>
                                <button
                                  type="button"
                                  className="btn-secondary"
                                  disabled={state.savingGitConfig}
                                  onClick={() => {
                                    const repo = document.getElementById(`repo-input-${project.id}`).value;
                                    const branch = document.getElementById(`branch-input-${project.id}`).value;
                                    handleSaveGitConfig(project.id, repo, branch);
                                  }}
                                >
                                  {state.savingGitConfig ? 'Saving...' : 'Save Repo'}
                                </button>
                                <button
                                  type="button"
                                  className="btn-sync"
                                  disabled={state.syncingGit || !project.repoIdentifier}
                                  onClick={() => handleSyncGit(project.id)}
                                >
                                  {state.syncingGit ? 'Syncing...' : 'Sync from GitHub'}
                                </button>
                                <button
                                  type="button"
                                  className="btn-inspect"
                                  disabled={state.generatingManifest || !(project.repoIdentifier || document.getElementById(`repo-input-${project.id}`)?.value)}
                                  onClick={() => {
                                    const repo = document.getElementById(`repo-input-${project.id}`)?.value || project.repoIdentifier;
                                    const branch = document.getElementById(`branch-input-${project.id}`)?.value || project.defaultBranch || 'main';
                                    handleGenerateManifest(project.id, repo, branch);
                                  }}
                                  title="Inspect repo descriptors and auto-generate candidate .llmhandoff.json"
                                >
                                  {state.generatingManifest ? 'Analyzing Repo...' : 'Auto-Generate Manifest'}
                                </button>
                              </div>

                              <div className="git-status-bar">
                                <span className="git-status-item">
                                  Status: <strong className={`status-${(project.gitSyncStatus || 'NOT_CONFIGURED').toLowerCase()}`}>{project.gitSyncStatus || 'NOT_CONFIGURED'}</strong>
                                </span>
                                {project.lastSyncedAt && (
                                  <span className="git-status-item">
                                    Last Synced: {new Date(project.lastSyncedAt).toLocaleString()}
                                  </span>
                                )}
                                {project.gitSyncError && (
                                  <span className="git-status-item error-text">
                                    Error: {project.gitSyncError}
                                  </span>
                                )}
                                {state.gitConfigSuccess && <span className="success-text">Git config saved!</span>}
                                {state.syncSuccess && <span className="success-text">Synced successfully!</span>}
                                {state.generateSuccess && <span className="success-text">Manifest draft generated! Review below.</span>}
                              </div>

                              {/* Inspection Summary Highlights */}
                              {state.generatedHighlights && (
                                <div className="inspection-highlights-box">
                                  <h5>Repository Auto-Inspection Summary</h5>
                                  <p>{state.generatedHighlights}</p>
                                  {state.detectedFiles && state.detectedFiles.length > 0 && (
                                    <div className="detected-files-list">
                                      <span>Detected Configs: </span>
                                      {state.detectedFiles.map((f, i) => (
                                        <code key={i} className="file-chip">{f}</code>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>

                            {/* Active Manifest Visual Dashboard */}
                            <div className="manifest-section">
                              <div className="manifest-header">
                                <h4>Active Manifest Overview</h4>
                                {manifest && (
                                  <button
                                    className="btn-copy-package"
                                    onClick={() => handleCopyHandoffPackage(project.id)}
                                  >
                                    {state.copySuccess ? 'Copied Prompt to Clipboard!' : 'Copy Handoff Package'}
                                  </button>
                                )}
                              </div>

                              {state.loadingManifest ? (
                                <p className="helper-text">Loading manifest data...</p>
                              ) : parsed ? (
                                <div className="dashboard-grid">
                                  {/* Environment info */}
                                  {parsed.environment && (
                                    <div className="dashboard-card">
                                      <h5>Environment & Tech Stack</h5>
                                      <div className="badge-group">
                                        {parsed.environment.language && <span className="chip chip-blue">{parsed.environment.language}</span>}
                                        {parsed.environment.framework && <span className="chip chip-purple">{parsed.environment.framework}</span>}
                                        {parsed.environment.database && <span className="chip chip-green">{parsed.environment.database}</span>}
                                        {parsed.environment.buildTool && <span className="chip chip-orange">{parsed.environment.buildTool}</span>}
                                        {parsed.environment.runtime && <span className="chip chip-teal">{parsed.environment.runtime}</span>}
                                      </div>
                                    </div>
                                  )}

                                  {/* Commands */}
                                  {parsed.commands && Object.keys(parsed.commands).length > 0 && (
                                    <div className="dashboard-card">
                                      <h5>Standard Commands</h5>
                                      <div className="commands-list">
                                        {Object.entries(parsed.commands).map(([k, v]) => (
                                          <div key={k} className="command-row">
                                            <span className="cmd-name">{k}:</span> <code>{v}</code>
                                          </div>
                                        ))}
                                      </div>
                                    </div>
                                  )}

                                  {/* Context Details */}
                                  {parsed.context && (
                                    <div className="dashboard-card full-width">
                                      <h5>Context & Directives</h5>
                                      {parsed.context.architecture && (
                                        <p className="architecture-text">{parsed.context.architecture}</p>
                                      )}
                                      {parsed.context.decisions && parsed.context.decisions.length > 0 && (
                                        <div className="checklist-block">
                                          <strong>Key Decisions:</strong>
                                          <ul>
                                            {parsed.context.decisions.map((d, i) => (
                                              <li key={i}>{d}</li>
                                            ))}
                                          </ul>
                                        </div>
                                      )}
                                      {parsed.context.rules && parsed.context.rules.length > 0 && (
                                        <div className="checklist-block">
                                          <strong>Development Rules:</strong>
                                          <ul>
                                            {parsed.context.rules.map((r, i) => (
                                              <li key={i}>{r}</li>
                                            ))}
                                          </ul>
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </div>
                              ) : (
                                <div className="empty-manifest-box">
                                  <p>No manifest uploaded yet for this project.</p>
                                  <p className="sub">Upload or paste a <code>.llmhandoff.json</code> below to enable deterministic prompt handoffs.</p>
                                </div>
                              )}
                            </div>

                            {/* JSON Ingestion & Editor Section */}
                            <div className="json-ingestion-section">
                              <h4>Upload or Edit <code>.llmhandoff.json</code></h4>
                              {state.hasDraft && (
                                <p className="preview-note">Review or modify the candidate JSON below, then click "Save & Activate Manifest".</p>
                              )}
                              <div className="file-input-wrapper">
                                <label className="file-upload-btn">
                                  Choose <code>.llmhandoff.json</code> File
                                  <input
                                    type="file"
                                    accept=".json,application/json"
                                    onChange={(e) => handleFileUpload(project.id, e.target.files[0])}
                                  />
                                </label>
                              </div>

                              <div className="json-editor-container">
                                <div className="editor-top-bar">
                                  <span className={`validation-badge ${state.isJsonValid ? 'valid' : 'invalid'}`}>
                                    {state.isJsonValid ? 'Valid JSON' : 'Invalid / Empty JSON'}
                                  </span>
                                </div>
                                <textarea
                                  className={`json-textarea ${state.isJsonValid ? 'json-valid' : state.rawInput ? 'json-invalid' : ''}`}
                                  placeholder='{\n  "schemaVersion": "1.0",\n  "project": {\n    "name": "My App",\n    "description": "App description"\n  }\n}'
                                  value={state.rawInput || ''}
                                  onChange={(e) => handleRawJsonChange(project.id, e.target.value)}
                                  rows={12}
                                />
                              </div>

                              <div className="save-action-row">
                                <button
                                  className="btn-save-manifest"
                                  onClick={() => handleSaveManifest(project.id)}
                                  disabled={!state.isJsonValid || state.saving}
                                >
                                  {state.saving ? 'Saving Manifest...' : 'Save & Activate Manifest'}
                                </button>
                                {state.saveSuccess && <span className="save-success-msg">Manifest saved and activated!</span>}
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </>
        )}

        {/* Authentication Modal */}
        <AuthModal
          isOpen={isAuthModalOpen}
          onClose={() => setIsAuthModalOpen(false)}
          onAuthSuccess={handleAuthSuccess}
        />
      </div>
    </div>
  );
}

export default App;
